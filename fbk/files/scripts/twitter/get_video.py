from twitter.noauth.scraper import Scraper 
import sys

tweet_id = sys.argv[1]

try:
    scraper = Scraper()
    tweet = scraper.tweets_by_id([tweet_id])[0]
    data = tweet['data']['tweetResult']['result']['legacy']['extended_entities']['media'][0]['video_info']['variants']
    print(data)

    max_index = -1
    max_bitrate = -1
    for i, v in enumerate(data):
        if(v['content_type'] == 'video/mp4'):
            if int(v['bitrate']) > max_bitrate:
                max_index = i

    sys.stdout.write("VIDEO ")
    if max_index != -1:
        video = data[max_index]['url']
        sys.stdout.write(video)
    else:
        sys.stdout.write("NONE")

except Exception as e:
    sys.stdout.write(f"ERRR {repr(e)}")
    sys.exit(5)

sys.stdout.write("\n")