import os
HERE=os.path.dirname(os.path.abspath(__file__))
REPO=os.path.dirname(os.path.dirname(HERE))
GT=HERE+'/gt'
WORK=os.environ.get('KAPOW_TRAINING_WORK',HERE+'/work')
COMICS=os.environ.get('KAPOW_COMICS',REPO+'/comics')
RAW=WORK+'/raw'; DATA=WORK+'/data'; PAGES=WORK+'/pages'; MODELS=WORK+'/models'; RUNS=WORK+'/runs'
DEVICE=os.environ.get('KAPOW_TRAINING_DEVICE','mps')
