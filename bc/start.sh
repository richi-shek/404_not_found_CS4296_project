#!/bin/bash

cleanup() {
  echo "Terminating background process..."
  kill $bg_pid
}

trap cleanup SIGINT

./frps -c ./frps.toml &

bg_pid=$!

java -jar BungeeCord.jar
