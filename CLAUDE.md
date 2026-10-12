# SwizzSMP plugin

Paper 26.3 plugin (Java 25) for the SwizzSMP Season 5 server.

- **Read `docs/DESIGN.md` first.** It is the source of truth for every rule, number and
  decision (D-xx). Keep it in sync with the code, and tick its §15 checklist as work lands.
- Paper API only: no NMS, no ProtocolLib/PacketEvents.
- Build: `build.cmd` (Windows) or
  `JAVA_HOME="<repo>/.tools/jdk-25" ./gradlew build`. The jar ends up in `build/libs/`.
- Boot test: `bash .tools/boottest.sh` copies the jar into `.tools/server`, starts
  Paper 26.3, runs a few console commands, stops it, and prints plugin log lines/errors.
- `.tools/` (portable JDK, Gradle, test server) is gitignored and machine-local.
- API lookups: the Paper API jar path is in `.tools/api-jar.txt`; inspect it with
  `.tools/jdk-25/bin/javap -cp "$(cat .tools/api-jar.txt)" <class>`.

## Conventions
- Package root `com.swizzsmp`; layout in DESIGN.md §14.
- Every gameplay number lives in `abilities.yml` / `config.yml`. Never hardcode balance values.
- Custom items are identified only by the PDC key `swizz:item`.
- Attribute changes are transient modifiers with `swizz:` keys. Every temporary world
  change needs a cleanup path for expiry, quit, death and plugin disable.
- Player-facing text goes through `messages.yml` (MiniMessage).
