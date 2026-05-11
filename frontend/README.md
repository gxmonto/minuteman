# Minuteman Frontend

React/Vite UI for the Minuteman Spring Boot API.

## Local setup

```bash
cp .env.example .env
npm install
npm run dev
```

The app expects the backend at `VITE_API_BASE_URL`, defaulting to `http://localhost:8080`.

## Current API calls

- `GET /test`
- `GET /api/work/current/{userId}`
- `POST /api/work/start-day/{userId}`
- `POST /api/work/end-day/{userId}`
- `POST /api/work/start-timer/{userId}?type=TASK`
- `POST /api/work/start-timer/{userId}?type=TRAVEL`
- `POST /api/work/stop-timer/{userId}`
