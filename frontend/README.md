# QueueDos Angular frontend

This subproject is the migration target for the current static Ktor frontend in `src/main/resources/static`.

## Development

Run the Ktor API on port `8080`, then start Angular:

```bash
npm install
npm start
```

The Angular dev server proxies `/api` requests to `http://localhost:8080`.
