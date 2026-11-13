#!/usr/bin/env bash
set -eu
exec 3<>/dev/tcp/127.0.0.1/${SERVER_PORT:-8087}
printf 'GET /actuator/health HTTP/1.0\r\nHost: localhost\r\n\r\n' >&3
read -r _ status _ <&3
test "$status" = 200
