#!/usr/bin/make -f

ZIG_VERSION ?= 0.13.0

.PHONY: all clean install sync-natives verify-natives zig-host zig-all setup-zig

all: zig-host

setup-zig:
	ZIG_VERSION=$(ZIG_VERSION) ./build-zig.sh --setup-only

sync-natives:
	./sync-natives.sh --sync

verify-natives:
	./sync-natives.sh --verify

zig-host: verify-natives
	ZIG_VERSION=$(ZIG_VERSION) ./build-zig.sh host

zig-all: verify-natives
	ZIG_VERSION=$(ZIG_VERSION) ./build-zig.sh all

install: zig-host

clean:
	rm -rf build/zig
