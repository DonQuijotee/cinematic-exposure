"""Use the environment HTTPS proxy with Java, retaining TLS certificate checks."""
import os
import shlex
import subprocess
import sys
from pathlib import Path
from urllib.parse import urlsplit

root = Path(__file__).resolve().parents[1]
proxy = urlsplit(os.environ.get('HTTPS_PROXY', ''))
options = []
if proxy.hostname:
    if proxy.username or proxy.password:
        raise SystemExit('Credential-bearing proxy URLs are not supported by this helper.')
    for protocol in ('http', 'https'):
        options.extend([f'-D{protocol}.proxyHost={proxy.hostname}',
                        f'-D{protocol}.proxyPort={proxy.port or 80}'])
    truststore = Path('/etc/ssl/certs/java/cacerts')
    if truststore.is_file():
        options.append(f'-Djavax.net.ssl.trustStore={truststore}')
env = os.environ.copy()
env['JAVA_OPTS'] = env.get('JAVA_OPTS', '') + ' ' + shlex.join(options)
raise SystemExit(subprocess.call([str(root / 'gradlew'), *sys.argv[1:], *options], cwd=root, env=env))
