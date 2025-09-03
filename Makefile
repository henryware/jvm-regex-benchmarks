files := $(shell find src -type f -name '*.scala' -not -name ".*" ) 

echo:
	echo ${files}

repl:
	scala repl .

jmh:
	scala run --power --jmh . -- -rf csv 2>&1 | tee .zlog

clean:
	scala clean .

test:
	TERM=vt100 scala test .

graph:
	echo "worldofregex.Graph.plot()" | scala repl .
