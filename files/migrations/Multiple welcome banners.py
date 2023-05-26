import os
import shutil

with os.scandir() as it:
    for entry in [f for f in it if f.name.endswith(".png")]:

        # old file name: server id.png
        fname = entry.name
        id = fname[0:len(fname)-4]

        print(f"Converting banner for {id}")
        # new directory name: server id
        os.makedirs(id)
        new = os.path.join(id, "banner.png")
        shutil.move(entry.path, new)
    