// http_status.js (env URL): output.httpStatus = the HTTP status of a GET of ${URL}, or 0 when the
// request itself failed (no route, DNS, TLS, timeout). Runs on the host (Maestro's JS), not on the
// device: it tells an outside service that is down from a build that is broken.
var status = 0
try {
  status = http.get(URL).status
} catch (e) {
  console.log('http_status: GET ' + URL + ' failed - ' + e)
}
output.httpStatus = status
console.log('http_status: GET ' + URL + ' -> ' + status)
