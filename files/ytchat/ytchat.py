import sys
import time

import pytchat

chat_id = sys.argv[1]
chat = pytchat.LiveChat(video_id=chat_id)
sys.stdout.write(f'JOIN {chat_id}\n')

sys.stdout.reconfigure(encoding='utf-8')
while chat.is_alive():
    chat_data = chat.get()
    sys.stdout.write(chat_data.json())
    sys.stdout.write('\n')
    time.sleep(3)

try:
    chat.raise_for_status()
except (pytchat.ChatDataFinished, pytchat.NoContents):
    sys.exit(0)
except Exception as e:
    sys.stdout.write(f'LOST {repr(e)}\n')
    sys.exit(1)
