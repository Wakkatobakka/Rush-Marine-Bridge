#!/usr/bin/env python3
"""Host checks, APK/source provenance, public boundary, optional original-guest tests."""
from pathlib import Path
import argparse,hashlib,json,os,subprocess,tempfile,xml.etree.ElementTree as ET,zipfile

ROOT=Path(__file__).resolve().parents[1]
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def check(ok,why):
    if not ok:raise AssertionError(why)
    print('PASS:',why,flush=True)
def run(args):subprocess.run([str(x) for x in args],cwd=ROOT,check=True)
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--public',action='store_true');args=parser.parse_args()
    project=json.loads((ROOT/'bridge-project.json').read_text());apk=ROOT/'dist'/project['apk_name']
    pins=json.loads((ROOT/'baseline/runtime-source-sha256.json').read_text())
    for rel,digest in pins.items():check(sha(ROOT/rel)==digest,'preserved runtime/shared source '+rel)
    manifest=ET.parse(ROOT/'app/AndroidManifest.xml').getroot();ns='{http://schemas.android.com/apk/res/android}'
    check(manifest.attrib['package']=='com.wakka.omnibridge.rushmarine','same Omni update package')
    check(manifest.attrib[ns+'versionCode']=='3' and manifest.attrib[ns+'versionName']=='0.1.2','increased versionCode / release version')
    check(not manifest.findall('uses-permission'),'no network or broad-storage permissions')
    for a in manifest.findall('application/activity'):
        check(a.attrib[ns+'exported']==('true' if a.attrib[ns+'name'].endswith('LauncherActivity') else 'false'),'private activity '+a.attrib[ns+'name'])
    p=manifest.find('application/provider');check(p.attrib[ns+'authorities']==project['package']+'.reports' and p.attrib[ns+'exported']=='false','private report provider')
    forbidden=('.mod','.mif','.bar','.sig','.keystore','.jks','.p12','.pfx')
    payload_specs=json.loads((ROOT/'expected-brew-files.json').read_text())['files']
    game_hashes={v['sha256'] for v in payload_specs}
    with zipfile.ZipFile(apk) as z:
        metadata=json.loads(z.read('assets/bridge-build.json'));inputs=metadata['inputs']
        for section in ['sources','payloads','resources','assets']:
            for rel,digest in inputs[section].items():check(sha(ROOT/rel)==digest,'APK fingerprint '+rel)
        check(inputs['payloads']=={},'no original game code passed to D8')
        check(sha(ROOT/'app/AndroidManifest.xml')==inputs['manifest_sha256'] and sha(ROOT/'bridge-project.json')==inputs['project_sha256'],'APK manifest/project provenance')
        check(hashlib.sha256(json.dumps(inputs,sort_keys=True).encode()).hexdigest()==metadata['build_input_id'],'APK source build identity')
        for name in z.namelist():
            check(not name.lower().endswith(forbidden) and Path(name).name not in ['progress.bin','prototype-identity.json'],'APK excludes game/signing file '+name)
            if not name.endswith('/'):
                check(hashlib.sha256(z.read(name)).hexdigest() not in game_hashes,'APK contains no renamed original payload '+name)
        check(not any(n.startswith('assets/brew/') for n in z.namelist()),'no embedded BREW data in APK')
    runner=(ROOT/'runtime/rushmarine/src/com/wakka/rushbridge/RuntimeRunner.java').read_text()
    check('payload.verify();' in runner and 'payload.read("277700/mmassault.mod")' in runner,'runtime uses verified imported files')
    check('readAll(app,"brew/' not in runner,'runtime has no embedded-game fallback')
    check('if(midi)stopOtherMidiPlayers(ev.mediaObject)' in runner,'single MIDI arbitration retained')
    check('hostAudioPaused?s.snapshot():s.pumpTimers' in runner and 'if(!hostAudioPaused){mp.start();audioHostStarts++;}' in runner,'timer/audio pause gates retained')
    backend=(ROOT/'profiles/rushmarine/src/com/wakka/rushbridge/BrewBackend.java').read_text()
    check('if(bootRequested)' in backend and 'RuntimeRunner.currentReport(app)' in backend,'reports/menu do not reboot the guest')
    check('BrewPayload.store(app.getFilesDir()).problem()==null' in backend,'play readiness verifies imported game data')
    game=(ROOT/'shared/src/com/wakka/bridge/BridgeGameActivity.java').read_text()
    check('new LinearLayout.LayoutParams(-1,0,1)' in game and 'ScrollView' not in game,'flexible non-scrolling gameplay stage')
    tools=(ROOT/'shared/src/com/wakka/bridge/BridgeToolsActivity.java').read_text()
    check('newPlainText("Runtime report",fullReport)' in tools,'Copy keeps full captured report')
    if args.public:
        excluded={'build','dist','signing','.git','__pycache__'}
        for f in ROOT.rglob('*'):
            if not f.is_file() or any(p in excluded for p in f.relative_to(ROOT).parts):continue
            check(not f.name.lower().endswith(forbidden) and f.name!='progress.bin','public source excludes payload/key '+f.relative_to(ROOT).as_posix())
            check(sha(f) not in game_hashes,'public source contains no renamed original payload '+f.relative_to(ROOT).as_posix())
            check(f.name!='prototype-identity.json','public source excludes signing credentials')
    runtime=ROOT/'runtime/rushmarine/src/com/wakka/rushbridge/runtime/BrewRuntime.java'
    router=ROOT/'profiles/rushmarine/src/com/wakka/rushbridge/BrewInputRouter.java'
    with tempfile.TemporaryDirectory(prefix='rush-release-tests-') as tmp:
        classes=Path(tmp)/'classes';classes.mkdir()
        probes=['HostBootProbe','LiveInputProbe','ControlMappingProbe','AudioBridgeProbe','AudioTransitionProbe']
        sources=[runtime,router,ROOT/'shared/src/com/wakka/bridge/InputLatch.java',ROOT/'shared/src/com/wakka/bridge/SessionGate.java',ROOT/'tests/BehaviorChecks.java',ROOT/'tests/GuestSnapshotChecks.java',ROOT/'tests/PayloadImportChecks.java',ROOT/'tests/RealPayloadImportProbe.java',ROOT/'profiles/rushmarine/src/com/wakka/rushbridge/VerifiedZipStore.java',ROOT/'profiles/rushmarine/src/com/wakka/rushbridge/BrewPayload.java']+[ROOT/'tests/probes'/f'{p}.java' for p in probes]
        run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','--release','17','-d',classes,*sources])
        run(['java','-cp',classes,'BehaviorChecks']);run(['java','-Xmx512m','-cp',classes,'PayloadImportChecks'])
        game_zip=os.environ.get('RUSH_MARINE_TEST_ZIP')
        if game_zip:
            imported=Path(tmp)/'app-files'
            run(['java','-cp',classes,'RealPayloadImportProbe',Path(game_zip).resolve(),imported])
            # Reproduce the OS-owned Android app-storage alias with real supported data.
            alias=Path(tmp)/'android-data-user-0';alias.symlink_to(imported,target_is_directory=True)
            run(['java','-cp',classes,'RealPayloadImportProbe',Path(game_zip).resolve(),alias])
            data=imported/'rushmarine-data/verified-v1/277700';guest_args=[data/'mmassault.mod',data/'progress.bin',data/'mmassaultbacksmall.bar']
            (ROOT/'verification').mkdir(exist_ok=True)
            for name in ['GuestSnapshotChecks']+probes:
                print('RUN:',name,flush=True);log=ROOT/'verification'/f'{name}.txt'
                with log.open('w') as out:subprocess.run(['java','-Xmx768m','-cp',str(classes),name,*map(str,guest_args)],cwd=ROOT,stdout=out,stderr=subprocess.STDOUT,check=True)
                content=log.read_text();print(content if name!='HostBootProbe' else '\n'.join(content.splitlines()[-12:]),flush=True)
        else:print('SKIP original-guest probes: set RUSH_MARINE_TEST_ZIP to your own compatible game ZIP.',flush=True)
    print('All selected host checks passed. New Android import/gameplay/audio/device acceptance remains a phone test.',flush=True)
if __name__=='__main__':main()
