import os
import tempfile
import time
import unittest
from pathlib import Path
from unittest.mock import patch

TEST_DATA = tempfile.mkdtemp(prefix="kemtiz-test-")
os.environ["KEMTIZ_DATA_DIR"] = TEST_DATA
os.environ["KEMTIZ_DB_PATH"] = str(Path(TEST_DATA) / "test.sqlite3")
os.environ["KEMTIZ_SECRET"] = "test-only-secret-change-me-1234567890"
os.environ["SMSRU_API_ID"] = "test-api-id"

from fastapi.testclient import TestClient
import server as server_module
from server import app


class KemtizApiTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.sent_codes = {}
        cls.phone_counter = 0

        async def fake_send_sms(phone, code):
            cls.sent_codes[phone] = code

        cls.sms_patcher = patch.object(server_module, "send_sms_code", new=fake_send_sms)
        cls.sms_patcher.start()
        cls.client_context = TestClient(app)
        cls.client = cls.client_context.__enter__()

    @classmethod
    def tearDownClass(cls):
        cls.client_context.__exit__(None, None, None)
        cls.sms_patcher.stop()

    @classmethod
    def new_phone(cls):
        cls.phone_counter += 1
        return "+7910" + f"{cls.phone_counter:07d}"

    def create_account(self):
        phone = self.new_phone()
        sent = self.client.post("/api/auth/request-code", json={"phone": phone})
        self.assertEqual(sent.status_code, 200, sent.text)
        self.assertEqual(sent.json()["phone"], phone)

        verified = self.client.post("/api/auth/verify-code", json={
            "phone": phone,
            "code": self.sent_codes[phone]
        })
        self.assertEqual(verified.status_code, 200, verified.text)
        data = verified.json()
        self.assertIn("token", data)
        self.assertNotIn("phone_number", data["user"])
        return phone, data, data["token"]

    def setUp(self):
        self.alice_phone, self.alice_data, self.alice_token = self.create_account()
        self.bob_phone, self.bob_data, self.bob_token = self.create_account()

    def auth(self, token):
        return {"Authorization": "Bearer " + token}

    def test_registration_and_phone_login(self):
        user_id = self.alice_data["user"]["id"]
        with server_module.db() as c:
            c.execute(
                "UPDATE auth_codes SET last_sent_at=? WHERE phone_number=?",
                (int(time.time()) - 61, self.alice_phone),
            )

        sent = self.client.post("/api/auth/request-code", json={"phone": self.alice_phone})
        self.assertEqual(sent.status_code, 200, sent.text)
        logged_in = self.client.post("/api/auth/verify-code", json={
            "phone": self.alice_phone,
            "code": self.sent_codes[self.alice_phone]
        })
        self.assertEqual(logged_in.status_code, 200, logged_in.text)
        self.assertEqual(logged_in.json()["user"]["id"], user_id)

    def test_friend_request_direct_chat_and_message(self):
        bob_username = self.bob_data["user"]["username"]
        sent = self.client.post(
            "/api/friends/requests",
            headers=self.auth(self.alice_token),
            json={"username": bob_username}
        )
        self.assertEqual(sent.status_code, 200, sent.text)

        inbox = self.client.get(
            "/api/friends/requests", headers=self.auth(self.bob_token)
        )
        self.assertEqual(inbox.status_code, 200, inbox.text)
        request_id = inbox.json()["incoming"][0]["request_id"]

        accepted = self.client.post(
            "/api/friends/requests/" + str(request_id) + "/accept",
            headers=self.auth(self.bob_token)
        )
        self.assertEqual(accepted.status_code, 200, accepted.text)

        bob_id = self.bob_data["user"]["id"]
        created = self.client.post(
            "/api/chats/direct/" + str(bob_id),
            headers=self.auth(self.alice_token)
        )
        self.assertEqual(created.status_code, 200, created.text)
        chat_id = created.json()["id"]

        sent_message = self.client.post(
            "/api/chats/" + str(chat_id) + "/messages",
            headers=self.auth(self.alice_token),
            json={"body": "Hello from tests"}
        )
        self.assertEqual(sent_message.status_code, 200, sent_message.text)
        self.assertEqual(sent_message.json()["body"], "Hello from tests")

        history = self.client.get(
            "/api/chats/" + str(chat_id) + "/messages",
            headers=self.auth(self.bob_token)
        )
        self.assertEqual(history.status_code, 200, history.text)
        self.assertEqual(history.json()[-1]["body"], "Hello from tests")

    def test_user_cannot_create_direct_chat_without_friendship(self):
        response = self.client.post(
            "/api/chats/direct/" + str(self.bob_data["user"]["id"]),
            headers=self.auth(self.alice_token)
        )
        self.assertEqual(response.status_code, 403)

    def test_code_format_is_validated(self):
        response = self.client.post("/api/auth/verify-code", json={
            "phone": self.alice_phone,
            "code": "12ab"
        })
        self.assertEqual(response.status_code, 422)

    def test_sms_requests_are_rate_limited(self):
        response = self.client.post("/api/auth/request-code", json={"phone": self.alice_phone})
        self.assertEqual(response.status_code, 429)


if __name__ == "__main__":
    unittest.main()
