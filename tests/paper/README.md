# Native Paper regression tests

These opt-in tests run an isolated Paper 26.3 server using JDK 25 and Python 3. Build the shaded plugin with the Gradle wrapper's `shadowJar` task. Supply the launcher from a previously started Paper 26.3 installation with its `cache`, `libraries`, `versions` and accepted `eula.txt` alongside it.

From the repository root run the following command, substituting the paths to your JARs.

```sh
python tests/paper/run.py --paper /path/to/server.jar --packetevents /path/to/packetevents-spigot-2.14.0.jar --blockengine build/libs/BlockEngine-1.0.9-RELEASE-all.jar
```

The gameplay probe creates a native server-side player and exercises placement, breaking, dispenser and cursor handling against real Paper worlds, inventories and events. Set `JAVA_HOME` to the JDK directory if Java 25 is outside your normal command path. The runner copies the server libraries into a temporary directory and starts a fresh world on a loopback port.

Append `--probe lifecycle` to run the two-phase persistence checks against the same temporary world across a server restart.
