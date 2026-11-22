"""Run the opt-in native Paper probes. See tests/paper/README.md."""

import argparse
import os
from pathlib import Path
import shutil
import subprocess
import tempfile


HERE = Path(__file__).resolve().parent


def jdk_tool(name):
    executable = name + (".exe" if os.name == "nt" else "")
    if "JAVA_HOME" in os.environ:
        return Path(os.environ["JAVA_HOME"]) / "bin" / executable
    return executable


def compile_probe(work, server, blockengine, probe_class):
    classes = work / "probe-classes"
    classes.mkdir()
    sources = HERE / (probe_class + ".java")
    libraries = sorted((work / "libraries").rglob("*.jar"), reverse=True)
    classpath = os.pathsep.join(path.as_posix() for path in [blockengine, server, *libraries])

    # An argument file avoids Windows' command-line length limit.
    arguments = work / "javac.args"
    arguments.write_text(
        f'-proc:none -encoding UTF-8 -cp "{classpath}" -d "{classes.as_posix()}" "{sources.as_posix()}"\n',
        encoding="utf-8",
    )
    subprocess.run([jdk_tool("javac"), "@" + str(arguments)], check=True)

    (classes / "paper-plugin.yml").write_text(
        f"name: {probe_class}\nmain: audit.{probe_class}\nversion: '1'\napi-version: '26.3'\n"
        "dependencies:\n  server:\n    blockengine:\n      load: BEFORE\n      required: true\n      join-classpath: true\n"
    )
    subprocess.run([jdk_tool("jar"), "cf", work / "plugins" / "Probe.jar", "-C", classes, "."], check=True)


def run_server(work, sentinel):
    result = subprocess.run(
        [jdk_tool("java"), "-Xms256m", "-Xmx1024m", "-jar", "server.jar", "--nogui", "--nojline", "--noconsole"],
        cwd=work,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        timeout=180,
        encoding="utf-8",
        errors="replace",
    )
    plugin_errors = any(" ERROR]:" in line and ("[BLOCKENGINE]" in line or "Probe]" in line)
                        for line in result.stdout.splitlines())
    if result.returncode or sentinel not in result.stdout or plugin_errors:
        print(result.stdout)
        raise SystemExit(f"Server verification failed while checking for {sentinel}")
    for line in result.stdout.splitlines():
        if "PASS" in line:
            print(line)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--paper", type=Path, required=True, help="Paper 26.3 launcher in a bootstrapped server directory")
    parser.add_argument("--packetevents", type=Path, required=True)
    parser.add_argument("--blockengine", type=Path, required=True, help="BlockEngine shaded plugin JAR")
    parser.add_argument("--probe", choices=["gameplay", "lifecycle"], default="gameplay")
    args = parser.parse_args()
    source = args.paper.resolve().parent
    with tempfile.TemporaryDirectory(prefix="blockengine-paper-") as directory:
        work = Path(directory)
        for name in ["cache", "libraries", "versions"]:
            shutil.copytree(source / name, work / name)
        shutil.copyfile(args.paper, work / "server.jar")
        shutil.copyfile(source / "eula.txt", work / "eula.txt")

        plugins = work / "plugins"
        (plugins / "blockengine").mkdir(parents=True)
        plugin = plugins / "BlockEngine.jar"
        shutil.copyfile(args.blockengine, plugin)
        shutil.copyfile(args.packetevents, plugins / "packetevents.jar")
        (plugins / "blockengine" / "config.yml").write_text("resource-pack:\n  enabled: false\n")
        (work / "server.properties").write_text(
            "server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nlevel-type=minecraft:flat\n"
            'generator-settings={"layers":[{"block":"minecraft:bedrock","height":1}],"biome":"minecraft:plains"}\n'
            "generate-structures=false\nspawn-protection=0\nview-distance=2\nsimulation-distance=2\n"
        )

        server = work / "versions" / "26.3" / "paper-26.3.jar"
        if args.probe == "gameplay":
            compile_probe(work, server, plugin, "BlockLifecycleProbe")
            run_server(work, "LIFECYCLE PROBE PASS")
        else:
            compile_probe(work, server, plugin, "PersistenceProbe")
            run_server(work, "PERSISTENCE PHASE 1 PASS")
            run_server(work, "PERSISTENCE PHASE 2 PASS")


if __name__ == "__main__":
    main()
