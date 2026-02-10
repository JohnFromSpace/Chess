## Runbook (single-node, file-based)

### Scope
This runbook is for a single-node server that persists to local files under `data/`.
It assumes a trusted/internal network (plain sockets, no TLS).

### Required files and directories
- `data/users.json` (user store)
- `data/games/*.json` (game store)
- `data/server-state.json` (heartbeat/state)
- `logs/` (server logs)

### Start
```
java -jar server/target/chess-server.jar \
  -Dchess.data.dir=data \
  -Dchess.server.port=5000
```

### Stop (graceful)
Use the process manager / OS signal:
- Windows: stop the process from Task Manager or your service wrapper
- Linux: `SIGTERM` to the server PID

### Restart
1. Stop the server (graceful).
2. Start the server with the same `chess.data.dir`.
3. Verify logs show: `Chess server starting...`

### Recovery
If the server fails to start or you suspect corrupted data:
1. Make a backup of `data/` first.
2. Run restore from a known-good backup:
   ```
   java -cp server/target/chess-server.jar com.example.chess.server.tools.DataBackupTool \
     restore backups/chess-data-<timestamp>.zip data --force
   ```
3. Start the server and monitor logs for warnings about quarantined files.

### Network isolation (plain sockets)
- Bind to a trusted interface only (firewall rules / allowlist).
- Only allow connections from internal networks or a VPN.
- Do not expose port `chess.server.port` directly to the public internet.

### Monitoring checklist
- `logs/server-*.log` rotates by size/count (see `Log` config).
- Watch for:
  - `Metric alert` warnings
  - `Failed to persist` / `Failed to parse` file errors
  - `Rejected client connection server overloaded`

### Common failures
- **Corrupt JSON file:** server quarantines it and logs a warning; restore from backup if needed.
- **High load:** you may see `Rejected client connection`; increase thread/queue settings or reduce load.
- **Disk full:** file writes fail; free space and restart.

### Config knobs (most used)
- `chess.data.dir` (default: `data`)
- `chess.server.port` (default: `5000`)
- `chess.server.threads.core` / `chess.server.threads.max`
- `chess.server.queue.capacity`
- `chess.socket.maxLineChars`
- `chess.socket.readTimeoutMs`
