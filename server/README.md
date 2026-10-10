# Lineup content publisher — Render setup

This FastAPI service accepts .lineup packages at POST /publish and commits them to content/packs/community.lineup. The GitHub Actions workflow then updates the public catalog and mandatory revision.

## Required Render environment variables
The service is configured as lineup-content-publisher with root directory / and start command uvicorn server.main:app --host 0.0.0.0 --port $PORT.

Set these values in Render Dashboard → service → Environment:
- LINEUP_GITHUB_REPO = Selteck1/ApkGhostly
- LINEUP_GITHUB_BRANCH = main
- LINEUP_ADMIN_KEY = admin publication key. It must exactly match the publisher key entered inside the app.
- LINEUP_GITHUB_TOKEN = a fine-grained GitHub personal access token.

## Create the GitHub token
1. Open GitHub Settings → Developer settings → Personal access tokens → Fine-grained tokens.
2. Create a token limited to the Selteck1/ApkGhostly repository.
3. Grant repository permission Contents: Read and write. The publisher only needs to upload/replace a file in content/packs/.
4. Copy the token once and set it as LINEUP_GITHUB_TOKEN in Render's Environment tab. Do not commit it into this repository or share it in chat.
5. Save changes and wait for the Render deploy to become Live.
6. Open /health on the service URL. Both githubConfigured and adminConfigured should be true.

## Test flow
1. In Lineup, create one or more blocks with a title, description and photos.
2. Go to Administrator → Publish database for all devices.
3. Enter the same admin key as LINEUP_ADMIN_KEY.
4. Wait for the success confirmation, then wait for the GitHub workflow Publish content catalog to complete.
5. Verify content/catalog.json now has a positive revision and at least one package.
6. On every device, choose Check and update database. It must download the current package and verify SHA-256 before unlocking content.

The free Render plan may suspend idle instances, so the first publishing request after inactivity can take longer while the service wakes.
