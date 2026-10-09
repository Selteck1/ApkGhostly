import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

TEST_DATA = tempfile.mkdtemp(prefix="kemtiz-test-")
os.environ["KEMTIZ_DATA_DIR"] = TEST_DATA
os.environ["KEMTIZ_DB_PATH"] = str(Path(TEST_DATA) / "test.sqlite3")
os.environ["KEMTIZ_SECRET"] = "test-only-secret-change-me-1234567890"
os.environ["KEMTIZ_GOOGLE_CLIENT_ID"] = "kemtiz-test.apps.googleusercontent.com"

from fastapi import HTTPException
from fastapi.testclient import TestClient
import server as server_module
from server import app


class KemtizApiTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.credentials = {}
        cls.identity_counter = 0
        cls.account_counter = 0

        async def fake_verify_google_credential(credential):
            identity = cls.credentials.get(credential)
            if identity is None:
                raise HTTPException(status_code=401, detail="Fake Google credential rejected.")
            return dict(identity)

        cls.google_patcher = patch.object(
            server_module,
            "verify_google_credential",
            new=fake_verify_google_credential,
        )
        cls.google_patcher.start()
        cls.client_context = TestClient(app)
        cls.client = cls.client_context.__enter__()

    @classmethod
    def tearDownClass(cls):
        cls.client_context.__exit__(None, None, None)
        cls.google_patcher.stop()

    @classmethod
    def create_identity(cls, name):
        cls.identity_counter += 1
        suffix = f"{cls.identity_counter:05d}"
        credential = "test-google-credential-" + suffix + "-" + name.lower()
        identity = {
            "sub": "google-sub-" + suffix,
            "email": name.lower() + "." + suffix + "@gmail.com",
            "name": name,
            "picture": "https://lh3.googleusercontent.com/test-" + suffix,
        }
        cls.credentials[credential] = identity
        return credential, identity

    def create_account(self, name):
        credential, identity = self.create_identity(name)
        type(self).account_counter += 1
        username = name.lower() + "_" + f"{type(self).account_counter:05d}"
        response = self.client.post("/api/auth/google/finish", json={
            "credential": credential,
            "username": username,
            "country": "Россия",
            "about": "Test profile"
        })
        self.assertEqual(response.status_code, 200, response.text)
        data = response.json()
        self.assertIn("token", data)
        self.assertEqual(data["user"]["email"], identity["email"])
        self.assertEqual(data["user"]["display_name"], name)
        self.assertEqual(data["user"]["username"], username)
        self.assertNotIn("password", data["user"])
        return credential, identity, data, data["token"]

    def test_password_register_login_and_wrong_password(self):
        username = "pass_user_01"
        password = "correct-horse-123"
        created = self.client.post("/api/auth/password/register", json={
            "username": username,
            "password": password,
            "display_name": "Password User",
        })
        self.assertEqual(created.status_code, 200, created.text)
        data = created.json()
        self.assertEqual(data["user"]["username"], username)
        self.assertEqual(data["user"]["display_name"], "Password User")
        self.assertNotIn("password_hash", data["user"])
        self.assertNotIn("password", data["user"])
        self.assertEqual(self.client.get("/api/me", headers=self.auth(data["token"])).status_code, 200)

        login = self.client.post("/api/auth/password/login", json={
            "username": username,
            "password": password,
        })
        self.assertEqual(login.status_code, 200, login.text)
        self.assertEqual(login.json()["user"]["id"], data["user"]["id"])

        wrong = self.client.post("/api/auth/password/login", json={
            "username": username,
            "password": "not-the-password",
        })
        self.assertEqual(wrong.status_code, 401)

    def test_password_registration_rejects_duplicate_login_and_short_password(self):
        username = "duplicate_01"
        payload = {"username": username, "password": "long-password-123"}
        first = self.client.post("/api/auth/password/register", json=payload)
        self.assertEqual(first.status_code, 200, first.text)
        duplicate = self.client.post("/api/auth/password/register", json=payload)
        self.assertEqual(duplicate.status_code, 409)
        too_short = self.client.post("/api/auth/password/register", json={
            "username": "another_01", "password": "123"
        })
        self.assertEqual(too_short.status_code, 422)

    def test_root_is_android_api_not_a_website(self):
        response = self.client.get("/")
        self.assertEqual(response.status_code, 200, response.text)
        self.assertEqual(response.json()["client"], "android-only")
        self.assertEqual(response.json()["app"], "Kemtiz API")
        self.assertEqual(response.json()["api_version"], "0.3.0")
        self.assertIn("username_password", response.json()["auth_methods"])

    def test_android_webview_origin_is_allowed_by_cors(self):
        response = self.client.options(
            "/api/config",
            headers={
                "Origin": "https://appassets.androidplatform.net",
                "Access-Control-Request-Method": "GET",
            },
        )
        self.assertEqual(response.status_code, 200, response.text)
        self.assertEqual(
            response.headers.get("access-control-allow-origin"),
            "https://appassets.androidplatform.net",
        )

    def setUp(self):
        self.alice_credential, self.alice_identity, self.alice_data, self.alice_token = self.create_account("Alice")
        self.bob_credential, self.bob_identity, self.bob_data, self.bob_token = self.create_account("Bob")

    def auth(self, token):
        return {"Authorization": "Bearer " + token}

    def test_google_login_returns_existing_user(self):
        response = self.client.post("/api/auth/google/start", json={
            "credential": self.alice_credential
        })
        self.assertEqual(response.status_code, 200, response.text)
        result = response.json()
        self.assertFalse(result["needs_profile"])
        self.assertEqual(result["user"]["id"], self.alice_data["user"]["id"])
        self.assertIn("token", result)

    def test_new_google_identity_requires_profile_then_registers(self):
        credential, identity = self.create_identity("Charlie")
        start = self.client.post("/api/auth/google/start", json={"credential": credential})
        self.assertEqual(start.status_code, 200, start.text)
        self.assertTrue(start.json()["needs_profile"])
        self.assertEqual(start.json()["profile"]["email"], identity["email"])

        finish = self.client.post("/api/auth/google/finish", json={
            "credential": credential,
            "username": "charlie_" + identity["sub"][-5:],
            "country": "Германия",
            "about": "Привет, Kemtiz!"
        })
        self.assertEqual(finish.status_code, 200, finish.text)
        self.assertEqual(finish.json()["user"]["country"], "Германия")
        self.assertEqual(finish.json()["user"]["about"], "Привет, Kemtiz!")

    def test_email_is_private_in_public_user_search(self):
        response = self.client.get(
            "/api/users/search?q=" + self.alice_data["user"]["username"],
            headers=self.auth(self.bob_token),
        )
        self.assertEqual(response.status_code, 200, response.text)
        self.assertEqual(len(response.json()), 1)
        self.assertNotIn("email", response.json()[0])
        self.assertIn("avatar_url", response.json()[0])

    def test_invalid_google_username_is_rejected(self):
        credential, _ = self.create_identity("Delta")
        response = self.client.post("/api/auth/google/finish", json={
            "credential": credential,
            "username": "invalid username",
            "country": "",
            "about": ""
        })
        self.assertEqual(response.status_code, 422)

    def test_unverified_google_credential_is_rejected(self):
        response = self.client.post("/api/auth/google/start", json={
            "credential": "unknown-google-credential-token"
        })
        self.assertEqual(response.status_code, 401)

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

    def test_password_accounts_can_register_become_friends_and_message(self):
        alice = self.client.post("/api/auth/password/register", json={
            "username": "password_alice_chat",
            "password": "long-password-123",
            "display_name": "Password Alice",
        })
        bob = self.client.post("/api/auth/password/register", json={
            "username": "password_bob_chat",
            "password": "long-password-456",
            "display_name": "Password Bob",
        })
        self.assertEqual(alice.status_code, 200, alice.text)
        self.assertEqual(bob.status_code, 200, bob.text)
        alice_token = alice.json()["token"]
        bob_token = bob.json()["token"]

        sent_request = self.client.post(
            "/api/friends/requests",
            headers=self.auth(alice_token),
            json={"username": "password_bob_chat"},
        )
        self.assertEqual(sent_request.status_code, 200, sent_request.text)

        inbox = self.client.get(
            "/api/friends/requests", headers=self.auth(bob_token)
        )
        self.assertEqual(inbox.status_code, 200, inbox.text)
        incoming = next(
            row for row in inbox.json()["incoming"]
            if row["username"] == "password_alice_chat"
        )
        accepted = self.client.post(
            "/api/friends/requests/" + str(incoming["request_id"]) + "/accept",
            headers=self.auth(bob_token),
        )
        self.assertEqual(accepted.status_code, 200, accepted.text)

        bob_id = bob.json()["user"]["id"]
        created = self.client.post(
            "/api/chats/direct/" + str(bob_id),
            headers=self.auth(alice_token),
        )
        self.assertEqual(created.status_code, 200, created.text)
        chat_id = created.json()["id"]

        sent_message = self.client.post(
            "/api/chats/" + str(chat_id) + "/messages",
            headers=self.auth(alice_token),
            json={"body": "Kemtiz password-auth chat works"},
        )
        self.assertEqual(sent_message.status_code, 200, sent_message.text)
        history = self.client.get(
            "/api/chats/" + str(chat_id) + "/messages",
            headers=self.auth(bob_token),
        )
        self.assertEqual(history.status_code, 200, history.text)
        self.assertEqual(history.json()[-1]["body"], "Kemtiz password-auth chat works")

    def test_user_cannot_create_direct_chat_without_friendship(self):
        response = self.client.post(
            "/api/chats/direct/" + str(self.bob_data["user"]["id"]),
            headers=self.auth(self.alice_token)
        )
        self.assertEqual(response.status_code, 403)


if __name__ == "__main__":
    unittest.main()
