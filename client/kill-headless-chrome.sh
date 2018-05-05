#!/usr/bin/env bash
set -e
kill $(ps -efw | grep "\--headless" | grep "Chrome" | grep -v grep | awk '{print $2}')
