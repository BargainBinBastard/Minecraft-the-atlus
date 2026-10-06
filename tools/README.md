# Tools

## prototype/

The JavaScript history simulator the Java engine was ported from. `ui-template.html` is the
browser page it ran in (the simulator is pasted in at `/*SIM*/`).

## parity/

Scripts that check the Java engine still matches the prototype exactly, seed for seed.

```sh
# Java side: compile the engine and the comparison programs
javac -encoding UTF-8 -d /tmp/hb src/main/java/io/github/bargainbinbastard/altus/history/*.java
javac -encoding UTF-8 -cp /tmp/hb -d /tmp/hb tools/parity/CompareMany.java tools/parity/FragMany.java

# Logs
java -cp /tmp/hb CompareMany 300 seed /tmp/java_many.txt
node tools/parity/jsmany.js 300 seed /tmp/js_many.txt
cmp /tmp/java_many.txt /tmp/js_many.txt && echo identical

# Fragments
java -cp /tmp/hb FragMany 300 seed /tmp/java_frag.txt
node tools/parity/jsfrag.js 300 seed /tmp/js_frag.txt
cmp /tmp/java_frag.txt /tmp/js_frag.txt && echo identical
```

Once the Java engine starts evolving past the prototype, the golden hashes in
`HistorySimulatorTest` will need updating and these scripts will be retired.
