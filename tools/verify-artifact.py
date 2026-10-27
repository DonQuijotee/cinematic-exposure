"""Static packaging checks only. Does not load Minecraft or start a JVM."""
import json
import struct
import sys
import tomllib
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parents[1]
jar = Path(sys.argv[1]) if len(sys.argv) > 1 else (
    root / 'build/libs/cinematic-exposure-1.21.1-neoforge-0.1.0-alpha.jar'
)
with zipfile.ZipFile(jar) as archive:
    names = set(archive.namelist())
    metadata = tomllib.loads(archive.read('META-INF/neoforge.mods.toml').decode())
    assert metadata['mods'][0]['modId'] == 'cinematic_exposure'
    assert metadata['mods'][0]['version'] == '0.1.0-alpha'
    dependencies = {dep['modId'] for dep in metadata['dependencies']['cinematic_exposure']}
    assert dependencies == {'minecraft', 'neoforge', 'exposure', 'cinematic'}
    config = json.loads(archive.read('cinematic_exposure.mixins.json'))
    assert not config.get('mixins'), 'All compatibility mixins must be client only'
    for mixin in config['client']:
        assert config['package'].replace('.', '/') + '/' + mixin + '.class' in names
    prefix = 'assets/cinematic_exposure/lang/'
    english = json.loads(archive.read(prefix + 'en_us.json'))
    chinese = json.loads(archive.read(prefix + 'zh_cn.json'))
    assert english.keys() == chinese.keys(), 'Language key mismatch'
    assert 'LICENSE' in names and 'THIRD_PARTY_NOTICES.md' in names
    classes = [name for name in names if name.endswith('.class')]
    assert classes, 'No compiled mod classes'
    for name in classes:
        assert name.startswith('io/github/donquijotee/cinematic_exposure/'), name
        magic, minor, major = struct.unpack('>IHH', archive.read(name)[:8])
        assert magic == 0xCAFEBABE and major == 65, (name, major)
    assert not any(name.endswith('.jar') for name in names), 'Dependencies must not be bundled'
print(f'PASS: {jar.name}; Java 21 classes, metadata, client mixins, languages and notices checked')
