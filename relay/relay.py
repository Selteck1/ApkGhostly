#!/usr/bin/env python3
import fcntl
import hashlib
import hmac
import ipaddress
import os
import select
import signal
import socket
import struct
import subprocess
import sys
import time
from dataclasses import dataclass

MAGIC = b"GHLY"
VERSION = 1
TYPE_HANDSHAKE = 1
TYPE_HANDSHAKE_REPLY = 2
TYPE_DATA = 3
TYPE_KEEPALIVE = 4
HMAC_SIZE = 16
HEADER_SIZE = 4 + 1 + 1 + 8 + 2

TUNSETIFF = 0x400454CA
IFF_TUN = 0x0001
IFF_NO_PI = 0x1000


def env(name: str, default: str) -> str:
    return os.getenv(name, default).strip()


TOKEN = env("GHOSTLY_RELAY_TOKEN", "")
BIND = env("GHOSTLY_RELAY_BIND", "0.0.0.0")
PORT = int(env("GHOSTLY_RELAY_PORT", "51888"))
TUN_NAME = env("GHOSTLY_TUN_NAME", "ghostly0")
TUN_SERVER_IP = env("GHOSTLY_TUN_SERVER_IP", "10.77.0.1")
TUN_CIDR = env("GHOSTLY_TUN_CIDR", "10.77.0.0/24")
CLIENT_TIMEOUT = int(env("GHOSTLY_CLIENT_TIMEOUT", "45"))

if not TOKEN or len(TOKEN) < 16:
    raise SystemExit("GHOSTLY_RELAY_TOKEN должен быть не короче 16 символов.")


@dataclass
class Client:
    addr: tuple[str, int]
    ip: str
    last_seen: float


class Relay:
    def __init__(self):
        self.stop = False
        self.tun_fd: int | None = None
        self.udp: socket.socket | None = None
        self.clients_by_addr: dict[tuple[str, int], Client] = {}
        self.clients_by_ip: dict[str, Client] = {}
        self.pool = list(ipaddress.ip_network(TUN_CIDR).hosts())[1:]
        self.wan_if = ""

    def run_cmd(self, *args: str, check=True):
        return subprocess.run(list(args), check=check, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

    def default_interface(self) -> str:
        out = subprocess.check_output(
            ["ip", "route", "show", "default"],
            text=True,
            stderr=subprocess.DEVNULL,
        )
        for line in out.splitlines():
            parts = line.split()
            if "dev" in parts:
                return parts[parts.index("dev") + 1]
        raise RuntimeError("Не найден WAN-интерфейс.")

    def configure_network(self):
        self.wan_if = self.default_interface()

        subprocess.run(
            ["sysctl", "-w", "net.ipv4.ip_forward=1"],
            check=True,
            stdout=subprocess.DEVNULL,
        )

        self.run_cmd("ip", "link", "set", "dev", TUN_NAME, "down", check=False)
        self.run_cmd("ip", "addr", "flush", "dev", TUN_NAME, check=False)

        self.run_cmd(
            "ip", "addr", "add", f"{TUN_SERVER_IP}/{ipaddress.ip_network(TUN_CIDR).prefixlen}",
            "dev", TUN_NAME,
        )
        self.run_cmd("ip", "link", "set", "dev", TUN_NAME, "mtu", "1280")
        self.run_cmd("ip", "link", "set", "dev", TUN_NAME, "up")

        self.ensure_iptables(
            ["-A", "FORWARD", "-i", TUN_NAME, "-j", "ACCEPT"],
            ["-D", "FORWARD", "-i", TUN_NAME, "-j", "ACCEPT"],
        )
        self.ensure_iptables(
            ["-A", "FORWARD", "-o", TUN_NAME, "-m", "conntrack", "--ctstate", "RELATED,ESTABLISHED", "-j", "ACCEPT"],
            ["-D", "FORWARD", "-o", TUN_NAME, "-m", "conntrack", "--ctstate", "RELATED,ESTABLISHED", "-j", "ACCEPT"],
        )
        self.ensure_iptables(
            ["-t", "nat", "-A", "POSTROUTING", "-s", TUN_CIDR, "-o", self.wan_if, "-j", "MASQUERADE"],
            ["-t", "nat", "-D", "POSTROUTING", "-s", TUN_CIDR, "-o", self.wan_if, "-j", "MASQUERADE"],
        )

    def ensure_iptables(self, add_rule: list[str], del_rule: list[str]):
        check_rule = add_rule.copy()
        if "-A" in check_rule:
            check_rule[check_rule.index("-A")] = "-C"
        result = subprocess.run(["iptables", *check_rule], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        if result.returncode != 0:
            subprocess.run(["iptables", *add_rule], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

    def open_tun(self):
        fd = os.open("/dev/net/tun", os.O_RDWR)
        ifr = struct.pack("16sH", TUN_NAME.encode(), IFF_TUN | IFF_NO_PI)
        fcntl.ioctl(fd, TUNSETIFF, ifr)
        self.tun_fd = fd

    def open_udp(self):
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.setsockopt(socket.SOL_SOCKET, socket.SO_RCVBUF, 4 * 1024 * 1024)
        s.setsockopt(socket.SOL_SOCKET, socket.SO_SNDBUF, 4 * 1024 * 1024)
        s.bind((BIND, PORT))
        self.udp = s

    @staticmethod
    def tag(data: bytes) -> bytes:
        return hmac.new(
            TOKEN.encode(),
            data,
            hashlib.sha256,
        ).digest()[:HMAC_SIZE]

    def handshake_reply(self, ip: str) -> bytes:
        raw_ip = socket.inet_aton(ip)
        body = MAGIC + bytes([VERSION, TYPE_HANDSHAKE_REPLY]) + raw_ip
        return body + self.tag(body)

    def allocate(self, addr: tuple[str, int]) -> Client:
        now = time.monotonic()
        existing = self.clients_by_addr.get(addr)
        if existing:
            existing.last_seen = now
            return existing

        used = {c.ip for c in self.clients_by_addr.values()}
        chosen = next((str(ip) for ip in self.pool if str(ip) not in used), None)
        if chosen is None:
            raise RuntimeError("Relay client pool is full.")

        client = Client(addr=addr, ip=chosen, last_seen=now)
        self.clients_by_addr[addr] = client
        self.clients_by_ip[chosen] = client
        return client

    def remove_expired(self):
        now = time.monotonic()
        for addr, client in list(self.clients_by_addr.items()):
            if now - client.last_seen > CLIENT_TIMEOUT:
                self.clients_by_addr.pop(addr, None)
                self.clients_by_ip.pop(client.ip, None)

    def handle_udp(self, data: bytes, addr: tuple[str, int]):
        if len(data) >= 8 and data[:4] == MAGIC and data[4] == VERSION and data[5] == TYPE_HANDSHAKE:
            token_len = struct.unpack("!H", data[6:8])[0]
            if len(data) != 8 + token_len:
                return
            supplied = data[8:].decode("utf-8", errors="ignore")
            if not hmac.compare_digest(supplied, TOKEN):
                return

            client = self.allocate(addr)
            client.last_seen = time.monotonic()
            self.udp.sendto(self.handshake_reply(client.ip), addr)
            return

        if len(data) < HEADER_SIZE + HMAC_SIZE:
            return
        if data[:4] != MAGIC or data[4] != VERSION:
            return

        msg_type = data[5]
        if msg_type not in (TYPE_DATA, TYPE_KEEPALIVE):
            return

        payload_len = struct.unpack("!H", data[14:16])[0]
        expected_len = HEADER_SIZE + payload_len + HMAC_SIZE
        if expected_len != len(data):
            return

        signed = data[:HEADER_SIZE + payload_len]
        supplied_tag = data[-HMAC_SIZE:]
        if not hmac.compare_digest(self.tag(signed), supplied_tag):
            return

        client = self.clients_by_addr.get(addr)
        if not client:
            return

        client.last_seen = time.monotonic()
        if msg_type == TYPE_KEEPALIVE:
            return

        payload = data[HEADER_SIZE:HEADER_SIZE + payload_len]
        if not payload or len(payload) < 20:
            return

        # Only forward IPv4 packets and only when the source is this client's
        # assigned tunnel address. This blocks address spoofing between clients.
        if payload[0] >> 4 != 4:
            return
        src = socket.inet_ntoa(payload[12:16])
        if src != client.ip:
            return

        os.write(self.tun_fd, payload)

    def read_tun(self) -> bytes:
        return os.read(self.tun_fd, 65535)

    def send_to_client(self, packet: bytes):
        if len(packet) < 20 or packet[0] >> 4 != 4:
            return

        dst = socket.inet_ntoa(packet[16:20])
        client = self.clients_by_ip.get(dst)
        if not client:
            return

        # Response packet format uses the same data envelope. Sequence is not
        # used for routing; it is retained for diagnostics/reordering.
        seq = int.from_bytes(os.urandom(8), "big")
        header = MAGIC + bytes([VERSION, TYPE_DATA]) + struct.pack("!QH", seq, len(packet))
        message = header + packet
        message += self.tag(message)
        try:
            self.udp.sendto(message, client.addr)
            client.last_seen = time.monotonic()
        except OSError:
            pass

    def shutdown(self):
        self.stop = True
        for cmd in (
            ["iptables", "-D", "FORWARD", "-i", TUN_NAME, "-j", "ACCEPT"],
            ["iptables", "-D", "FORWARD", "-o", TUN_NAME, "-m", "conntrack", "--ctstate", "RELATED,ESTABLISHED", "-j", "ACCEPT"],
            ["iptables", "-t", "nat", "-D", "POSTROUTING", "-s", TUN_CIDR, "-o", self.wan_if, "-j", "MASQUERADE"],
        ):
            try:
                subprocess.run(cmd, check=False, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            except Exception:
                pass

        try:
            if self.udp:
                self.udp.close()
        except Exception:
            pass

        try:
            if self.tun_fd is not None:
                os.close(self.tun_fd)
        except Exception:
            pass

    def serve(self):
        self.open_tun()
        self.configure_network()
        self.open_udp()

        print(f"Ghostly relay listening on udp://{BIND}:{PORT}")
        print(f"Tunnel network: {TUN_CIDR}")
        print(f"WAN interface: {self.wan_if}")

        while not self.stop:
            readables = [self.udp, self.tun_fd]
            readable, _, _ = select.select(readables, [], [], 1.0)

            for item in readable:
                if item is self.udp:
                    try:
                        data, addr = self.udp.recvfrom(65535)
                        self.handle_udp(data, addr)
                    except OSError:
                        pass
                else:
                    try:
                        packet = self.read_tun()
                        self.send_to_client(packet)
                    except OSError:
                        self.stop = True

            self.remove_expired()


relay = Relay()


def stop_handler(signum, frame):
    relay.shutdown()


signal.signal(signal.SIGTERM, stop_handler)
signal.signal(signal.SIGINT, stop_handler)


if __name__ == "__main__":
    if os.geteuid() != 0:
        raise SystemExit("Запускай relay от root.")
    try:
        relay.serve()
    finally:
        relay.shutdown()
