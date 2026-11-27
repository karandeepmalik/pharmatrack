# Included inside every location block in nginx.conf, not just at server level — nginx resets
# the inherited add_header set for any location that defines its own add_header (e.g. the
# Cache-Control override on static assets, or Content-Type on /health), so a server-level-only
# declaration would silently vanish from exactly those responses. Textual inclusion inside each
# location sidesteps that inheritance rule entirely. `always` ensures these are sent on error
# responses (4xx/5xx) too, not just 2xx/3xx.
add_header X-Content-Type-Options "nosniff" always;
add_header X-Frame-Options "DENY" always;
add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
# script-src/style-src deliberately exclude 'unsafe-inline' for scripts (the Vite production
# build emits only external hashed <script>/<link> files, confirmed against a real build) but
# allow it for styles, since that's a much lower-severity relaxation than inline script and
# covers any future inline <style> Vite might emit. connect-src is scoped to this app's actual
# backend origin, not a wildcard, since this governs what the browser is allowed to fetch/XHR to.
add_header Content-Security-Policy "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self' https://pharmatrack-backend-558147403401.asia-south1.run.app; frame-ancestors 'none'; base-uri 'self'; form-action 'self';" always;
add_header Permissions-Policy "geolocation=(), microphone=(), camera=()" always;
add_header Cross-Origin-Opener-Policy "same-origin" always;
add_header Cross-Origin-Resource-Policy "same-origin" always;
