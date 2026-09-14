from pathlib import Path
from PySide6.QtWidgets import QApplication
from PySide6.QtSvg import QSvgRenderer
from PySide6.QtGui import QImage, QPainter
from PySide6.QtCore import Qt
from PIL import Image
Path('artifacts').mkdir(exist_ok=True)
app=QApplication([])
canvas=QImage(1024,1024,QImage.Format.Format_ARGB32)
canvas.fill(Qt.GlobalColor.transparent)
painter=QPainter(canvas)
renderer=QSvgRenderer('assets/app-icon.svg')
assert renderer.isValid()
renderer.render(painter);painter.end()
canvas.save('assets/app-icon.png')
im=Image.open('assets/app-icon.png')
im.save('assets/app-icon.ico',sizes=[(16,16),(24,24),(32,32),(48,48),(64,64),(128,128),(256,256)])
im.resize((256,256),Image.Resampling.LANCZOS).save('artifacts/icon-preview.png')
with Image.open('assets/app-icon.ico') as icon:
 assert len(icon.ico.sizes())==7
print('PASS: SVG rendered, multi-size ICO contains 7 resolutions')
