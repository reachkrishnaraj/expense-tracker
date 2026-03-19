#!/bin/sh
# Substitute only our custom env vars in nginx config, preserving nginx's own $vars
envsubst '${PORT} ${BACKEND_URL}' < /etc/nginx/nginx.conf.template > /etc/nginx/conf.d/default.conf
exec nginx -g 'daemon off;'
