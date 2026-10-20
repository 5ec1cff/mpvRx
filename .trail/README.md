# .trail

What coding sessions on this repo worked out, kept next to the code so the
next session, yours or a teammate's, starts from it instead of digging again.

- learnings/   one file per session that took real digging: what was decided,
               what was tried and ruled out, and what to watch out for, plus
               what it took to work out
- hook.cjs     what Claude Code runs at session start (`.claude/settings.json`).
               With trail installed it hands over to trail. Without it, it
               shows Claude these learnings and offers to set trail up.

Commit this folder and `.claude/settings.json` once. After that, commit new
learnings with the change they came from. Learnings are short summaries, never
transcripts, and plain markdown without trail installed. What
`.trail/.gitignore` lists stays on each machine.

## Local MCP setup

Install Node.js 20 or newer and put the Trail CLI on your `PATH`:

```sh
npm install --global @trailhq/trail-code
```

The [VS Code configuration](../.vscode/mcp.json) and
[other MCP clients' configuration](../.mcp.json) launch `trail mcp` over stdio.
Start or restart Trail in your MCP client after changing the configuration.
Each client manages its own stdio connection; there is no HTTP port to open.

These configurations disable automatic graph refresh, telemetry, and historical
session imports. They use the existing local graph without running a graph build.
