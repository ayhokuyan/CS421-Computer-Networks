import os

os.system("python test_retrieve.py | python test_write.py | python test_modify.py")
os.system("python test_revert.py")