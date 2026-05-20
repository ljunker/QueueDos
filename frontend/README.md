# QueueDos Angular frontend

This subproject contains the QueueDos Angular frontend.

The app uses standalone components, zoneless change detection, Atomic Design folders under `shared/atoms`, `shared/molecules`, and `shared/organisms`, and NgRx Store/Effects for server state, UI state, URL state, mutations, and authentication.

## Development

Run the Ktor API on port `8080`, then start Angular:

```bash
npm install
npm start
```

The Angular dev server proxies `/api` requests to `http://localhost:8080`.
