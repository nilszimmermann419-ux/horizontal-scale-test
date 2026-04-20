.PHONY: build test clean run

build:
	go build -o bin/coordinator ./cmd/coordinator

test:
	go test ./...

clean:
	rm -rf bin/

run: build
	./bin/coordinator
