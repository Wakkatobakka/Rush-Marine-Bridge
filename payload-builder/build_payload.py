#!/usr/bin/env python3
"""Optional local helper for users who have extracted BREW files rather than a ZIP."""
from pathlib import Path
import argparse,hashlib,json,os,tempfile,zipfile

ROOT=Path(__file__).resolve().parent
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('source',nargs='?',help='Folder containing your compatible extracted BREW package')
    parser.add_argument('--output',type=Path,default=ROOT/'Rush_Marine_Data_for_Bridge_v0.1.2.zip');args=parser.parse_args()
    source=args.source
    if not source:
        try:
            import tkinter as tk
            from tkinter import filedialog
            window=tk.Tk();window.withdraw();source=filedialog.askdirectory(title='Select the Rush Marine folder containing 277700.mif and the 277700 folder');window.destroy()
        except Exception as error:raise SystemExit('Folder picker unavailable. Run: python build_payload.py PATH_TO_GAME_FOLDER') from error
        if not source:print('Cancelled. No ZIP created.');return
    directory=Path(source).expanduser().resolve()
    if not directory.is_dir():raise SystemExit('Select a folder containing your compatible extracted BREW files.')
    specs=json.loads((ROOT/'expected-brew-files.json').read_text())['files'];selected={}
    count=0
    for file in directory.rglob('*'):
        count+=1
        if count>4096:raise SystemExit('Select the game folder itself, not a large parent folder.')
        if not file.is_file():continue
        relative=file.relative_to(directory).as_posix()
        for spec in specs:
            name=spec['path']
            if relative==name or relative.endswith('/'+name):
                if file.is_symlink():raise SystemExit('Game files must be ordinary local files, not symbolic links.')
                if name in selected:raise SystemExit('Multiple copies of '+name+' found. Select one game folder.')
                if file.stat().st_size!=spec['bytes'] or hashlib.sha256(file.read_bytes()).hexdigest()!=spec['sha256']:
                    raise SystemExit('Unsupported or modified '+name+'. Expected English BREW 1.1.11, CDM2030 128x160.')
                selected[name]=file
    missing=[spec['path'] for spec in specs if spec['path'] not in selected]
    if missing:raise SystemExit('Missing: '+', '.join(missing)+'. Select the parent folder containing 277700.mif and the 277700 folder.')
    output=args.output.expanduser().resolve();output.parent.mkdir(parents=True,exist_ok=True)
    fd,temporary=tempfile.mkstemp(prefix='rush-data-',suffix='.zip',dir=output.parent);os.close(fd)
    try:
        with zipfile.ZipFile(temporary,'w',zipfile.ZIP_DEFLATED) as archive:
            for spec in specs:
                # Recheck the bytes immediately before writing the output.
                data=selected[spec['path']].read_bytes()
                if len(data)!=spec['bytes'] or hashlib.sha256(data).hexdigest()!=spec['sha256']:raise SystemExit('Input changed while building the ZIP.')
                archive.writestr(spec['path'],data)
        os.replace(temporary,output)
    finally:
        if os.path.exists(temporary):os.unlink(temporary)
    print('Created: '+str(output));print('Copy this ZIP to your phone, tap IMPORT RUSH MARINE ZIP, then ENTER RUSH MARINE.')
if __name__=='__main__':main()
