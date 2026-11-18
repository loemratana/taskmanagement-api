# Deploying to Windows Server

Every push to `main` builds and tests the API on GitHub, then a **self-hosted runner on the Windows
Server** downloads the jar and runs [`deploy.ps1`](deploy.ps1):

```
git push main -> test (ubuntu-latest): gradle build + tests, upload app.jar
              -> deploy (self-hosted, windows, tms-prod):
                   pg_dump backup -> stop service -> swap jar -> start
                   -> poll /management/health/readiness (DB + MinIO)
                   -> roll back to app.jar.bak if not healthy in 180 s
```

The runner connects *out* to GitHub, so no inbound SSH/RDP is needed for deploys.

> **Private repo only.** On a public repo, code from fork PRs could run on a self-hosted runner.

## Files

| File | Goes to | Purpose |
|---|---|---|
| `deploy.ps1` | runs from the checkout | Deploy + health check + rollback |
| `tms-api.xml` | `E:\company\applications\task-management\` | WinSW service for the API |
| `web.config` | IIS site folder | HTTPS reverse proxy to `localhost:8082` |

## One-time server setup

### 1. Runtime and data services

PostgreSQL and MinIO are already installed on the server. Only Java is new.

1. **Java 21**: install the Eclipse Temurin 21 JDK/JRE MSI and tick *Add to PATH* and *Set JAVA_HOME*.
   Check it with `java -version`.
2. **PostgreSQL (already installed)**: create the database and a dedicated user, if you haven't yet:
   ```sql
   CREATE USER tms WITH PASSWORD '...';
   CREATE DATABASE tms OWNER tms;
   ```
   Make sure `pg_dump` works from a new PowerShell window (`pg_dump --version`). If it doesn't, add the
   install's `bin` folder (for example `C:\Program Files\PostgreSQL\17\bin`) to the system PATH. `deploy.ps1`
   uses it for the pre-deploy backup. The `pg_dump` version must be the same as the server or newer.
3. **MinIO (already installed)**: note its **S3 API port** (the default is 9000, while this repo's docker
   setup uses 9100) and its root user and password. Check it answers with:
   ```powershell
   Invoke-WebRequest http://localhost:9000/minio/health/ready -UseBasicParsing   # use your port
   ```
   Make sure MinIO runs as a Windows service that starts automatically (`Get-Service *minio*`). If you
   only start it by hand in a console, it won't come back after a reboot and the API will fail readiness.
4. Find the exact service names with `Get-Service *postgres*, *minio*`. You can optionally put them into the
   commented `<depend>` lines in `tms-api.xml`, so the API starts after them on boot.

### 2. The API service

1. Create `E:\company\applications\task-management` and copy `WinSW-x64.exe` there as `tms-api.exe` and `tms-api.xml` next to it.
2. Create `E:\company\applications\task-management\.env` from `.env.example` with **production** values:
   - `SPRING_PROFILES_ACTIVE=prod`, `SERVER_PORT=8082`
   - `DB_HOST=localhost`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` (the user from step 1)
   - `MINIO_ENDPOINT=http://localhost:<your MinIO API port>`, `MINIO_PUBLIC_ENDPOINT=https://taskfiles.company.com`,
     `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` (the credentials of your existing MinIO), `MINIO_BUCKET`
     (the app creates the bucket at startup if it's missing)
   - a new `JWT_SECRET` (`openssl rand -base64 48`, or in PowerShell:
     `[Convert]::ToBase64String((1..48 | % { Get-Random -Max 256 }))`)
   - `CLIENT_BASE_URL`, `CORS_ALLOWED_ORIGINS`, SMTP, Firebase and Telegram as needed
3. Lock the file down so only admins and the runner account can read it:
   ```powershell
   icacls E:\company\applications\task-management\.env /inheritance:r /grant:r "Administrators:F" "SYSTEM:F" "<runner-account>:R"
   ```
4. Install the service as admin. It starts on the first deploy.
   ```powershell
   E:\company\applications\task-management\tms-api.exe install
   ```
   If you later edit `tms-api.xml` (for example to enable the `<depend>` lines), apply the change with
   `tms-api.exe refresh`.

### 3. GitHub self-hosted runner

1. In GitHub, open **Settings > Actions > Runners > New self-hosted runner > Windows**. Follow the commands
   in `C:\actions-runner`.
2. When asked for labels, enter **`tms-prod`**.
3. Answer **Y** to *run as service*. Use a dedicated local account (for example `svc-deploy`) that can:
   - write to `E:\company\applications\task-management`
   - start and stop the `tms-api` service (simplest: make it a member of local Administrators)
   - read `E:\company\applications\task-management\.env` (for the database backup)
4. Optional but recommended: open **Settings > Environments > production** and add *Required reviewers*
   so each deploy waits for an approval.

### 4. HTTPS with IIS

1. Install IIS, plus **URL Rewrite** and **Application Request Routing**. In IIS Manager, go to server
   node > *Application Request Routing Cache* > *Server Proxy Settings* and tick **Enable proxy**.
2. **DNS:** point both names at the server's IP with A records:
   - `taskapi.company.com`: the API
   - `taskfiles.company.com`: MinIO. Attachment links are pre-signed for this host, so it needs its own
     name; it can't be a sub-path of the API site.

   Check with `Resolve-DnsName taskapi.company.com`.
3. Create the two IIS sites, bound by host name (run as admin):
   ```powershell
   Import-Module WebAdministration
   New-Item -ItemType Directory -Force C:\inetpub\taskapi, C:\inetpub\taskfiles
   New-Website -Name taskapi   -PhysicalPath C:\inetpub\taskapi   -Port 80 -HostHeader taskapi.company.com
   New-Website -Name taskfiles -PhysicalPath C:\inetpub\taskfiles -Port 80 -HostHeader taskfiles.company.com
   ```
   Copy `web.config` into `C:\inetpub\taskapi`. Copy it into `C:\inetpub\taskfiles` as well, changing
   `localhost:8082` to `localhost:<MinIO API port>` and the rule name to `ReverseProxyToMinio`.
4. Go to server node > *URL Rewrite* > *View Server Variables* and add **`HTTP_X_FORWARDED_PROTO`**
   (otherwise the rule returns HTTP 500).
5. **HTTPS certificate:**
   - **Public DNS** (reachable from the internet on port 80): run **win-acme** (`wacs.exe`) and pick both
     IIS sites. It adds the 443 bindings and renews automatically.
   - **Internal-only DNS** (company network): Let's Encrypt can't validate it. Import a certificate from
     your company CA (or a `*.company.com` wildcard) and add an `https` binding on 443 with SNI for each
     host name in IIS Manager.
6. In `.env` set `MINIO_PUBLIC_ENDPOINT=https://taskfiles.company.com`, and set `CLIENT_BASE_URL` /
   `CORS_ALLOWED_ORIGINS` to your frontend's URL.
7. **Windows Firewall:** allow inbound 80 and 443 only. Keep 8082, 5432 and the MinIO API/console ports closed from outside.

## First deploy

Push to `main` (or re-run the latest `main` workflow). Watch the **Deploy** step. It prints the last
60 lines of the service logs if the app does not come up.

## Day-to-day

| Task | How |
|---|---|
| Logs | `E:\company\applications\task-management\tms-api.out.log` / `tms-api.err.log` (rotated) |
| Restart | `Restart-Service tms-api` |
| Roll back a release | Re-run an older successful workflow run (artifacts kept 14 days), or copy `app.jar.bak` over `app.jar` and restart |
| DB backups | `E:\company\applications\task-management\backups\*.dump` (last 10, one per deploy). Restore with `pg_restore -d tms -c <file>` |
| Deploy by hand | `.\deploy\windows\deploy.ps1 -JarPath path\to\app.jar` |
| Skip the backup | `deploy.ps1 ... -SkipDbBackup` (also skipped automatically when `DB_URL` points at an external DB) |

A rollback restores the old **jar**, not the old **schema**. Flyway migrations from the failed release stay
applied, so keep migrations backward-compatible or restore the pre-deploy dump.

Schedule a nightly `pg_dump` with Task Scheduler as well. The per-deploy dumps are not a backup strategy
on their own.
