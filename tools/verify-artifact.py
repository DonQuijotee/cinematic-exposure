"""Packaging/ABI checks and isolated addon geometry math. Never loads or starts Minecraft."""
import json
import struct
import sys
import tomllib
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parents[1]
jar = Path(sys.argv[1]) if len(sys.argv) > 1 else (
    root / 'build/libs/cinematic-exposure-1.21.1-neoforge-0.2.5-alpha.jar'
)
with zipfile.ZipFile(jar) as archive:
    names = set(archive.namelist())
    metadata = tomllib.loads(archive.read('META-INF/neoforge.mods.toml').decode())
    assert metadata['mods'][0]['modId'] == 'cinematic_exposure'
    assert metadata['mods'][0]['version'] == '0.2.5-alpha'
    dependencies = {dep['modId'] for dep in metadata['dependencies']['cinematic_exposure']}
    assert dependencies == {'minecraft', 'neoforge', 'exposure', 'cinematic'}
    config = json.loads(archive.read('cinematic_exposure.mixins.json'))
    assert config.get('mixins') == ['LightroomMenuProviderMixin'], 'Only the menu-provider adapter is common'
    for mixin in config['client'] + config['mixins']:
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
print(f'PASS: {jar.name}; Java 21 classes, metadata, mixin classes, languages and notices checked')

# Inspect dependency bytecode only; never bootstrap Minecraft or load the game classes.
import os
import shutil
import subprocess
javap = str(Path(os.environ['JAVA_HOME']) / 'bin/javap') if os.environ.get('JAVA_HOME') else shutil.which('javap')
assert javap, 'A JDK with javap is required for the drawer adapter check'
host = root / 'build/dependencies/CinematicInteraction-0.18.12-NeoForge-1.21.1.jar'
abi = subprocess.run([javap, '-p', '-s', '-c', '-classpath', str(host),
                      'dev.dyslexic.cinematic.client.SceneScreen'], check=True, capture_output=True, text=True).stdout
for signature in [
    'private double expand;', 'private float scale;',
    'private int panelX;', 'private int panelY;', 'private int panelW;', 'private int panelH;',
    'protected void init();', 'private boolean overPanel(double, double);', 'private int slotAt(double, double);',
    'private void drawPanel(net.minecraft.client.gui.GuiGraphics, net.minecraft.client.player.LocalPlayer, int, float);',
    'GuiGraphics.renderItem:(Lnet/minecraft/world/item/ItemStack;II)V',
    'GuiGraphics.renderItemDecorations:(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V',
]:
    assert signature in abi, f'Cinematic drawer ABI changed: {signature}'
print('PASS: actual Cinematic JAR contains the drawer adapter targets; no game classes executed')

hands = subprocess.run([javap, '-p', '-s', '-classpath', str(host),
                        'dev.dyslexic.cinematic.client.Hands'], check=True, capture_output=True, text=True).stdout
for signature in [
    'static net.minecraft.world.phys.Vec3 position(boolean);',
    'static void drawArm(com.mojang.blaze3d.vertex.PoseStack, net.minecraft.client.renderer.MultiBufferSource, net.minecraft.world.phys.Vec3, net.minecraft.world.phys.Vec3, net.minecraft.world.phys.Vec3, boolean, int, net.minecraft.world.phys.Vec3);',
]:
    assert signature in hands, f'Cinematic hands ABI changed: {signature}'
print('PASS: actual Cinematic JAR contains both hand-rendering/position invoker targets')

# Execute only CameraAssembly's pure Java math with no Minecraft/dependency classes on the classpath.
java = str(Path(os.environ['JAVA_HOME']) / 'bin/java') if os.environ.get('JAVA_HOME') else shutil.which('java')
assert java, 'Java 21 is required for the isolated assembly geometry check'
subprocess.run([java, '-Xmx256m', '--class-path', str(jar),
                str(root / 'tools/CheckCameraAssembly.java')], check=True)
