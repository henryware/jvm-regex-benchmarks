re2: lib/libre2_c.so

lib/libre2_c.so: lib/re2_c.cpp
	g++ -shared -fPIC -O2 -o $@ $< -lre2

repl:
	scala repl .

test:
	scala test .

bench:
	scala run --power --jmh . -- -rf csv 2>&1 | tee .zlog

plot:
	echo "worldofregex.Graph.plot()" | scala repl .

