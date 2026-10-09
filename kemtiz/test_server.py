import os
import tempfile
import unittest
from pathlib import Path

TEST_DATA = tempfile.mkdtemp(prefix="kemtiz-test-")
os.environ["KEMTIZ_DATA_DIR"] = TEST_DATA
os.environ["KEMTIZ_DB_PATH"] = str(Path(TEST_DATA) / "test.sqlite3")
os.environ["KEMTIZ_SECRET"] = "test-only-secret-change-me-1234567890"

from fastapi.testclient import TestClient
from server import app


class KemtizApiTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.client_context = TestClient(app)
        cls.client = cls.client_context.__enter__()

    @classmethod
    def tearDownClass(cls):
        cls.client_context.__exit__(None, None, None)

    def setUp(self):
        self.alice = self.client.post("/api/auth/register", json={
            "username": "alice_" + str(id(self))[-5:],
            "display_name": "Alice",
            "password": "TestPassword123!"
        })
        self.assertEqual(self.alice.status_code, 200, self.alice.text)
        self.alice_data = self.alice.json()
        self.alice_token = self.alice_data["token"]

        self.bob = self.client.post("/api/auth/register", json={
            "username": "bob_" + str(id(self))[-5:],
            "display_name": "Bob",
            "password": "AnotherPassword123!"
        })
        self.assertEqual(self.bob.status_code, 200, self.bob.text)
        self.bob_data = self.bob.json()
        self.bob_token = self.bob_data["token"]

    def auth(self, token):
        return {"Authorization": "Bearer " + token}

    def test_registration_and_login(self):
        username = self.alice_data["user"]["username"]
        response = self.client.post("/api/auth/login", json={
            "username": username, "password": "TestPassword123!"
        })
        self.assertEqual(response.status_code, 200, response.text)
        self.assertIn("token", response.json())

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

    def test_user_cannot_read_chat_without_membership(self):
        response = self.client.post(
            "/api/chats/direct/" + str(self.bob_data["user"]["id"]),
            headers=self.auth(self.alice_token)
        )
        # Direct chat creation must fail before friendship.
        self.assertEqual(response.status_code, 403)


if __name__ == "__main__":
    unittest.main()
