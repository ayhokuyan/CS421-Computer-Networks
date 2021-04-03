# -*- coding: utf-8 -*-
import socket

ENCODING = "ascii"
NEWLINE = "\r\n"
USERNAME = "bilkent"
PASS = "cs421"

TARGET_FILENAME = "target.jpg"
SAVE_FILENAME = "received.jpg"

HEADER_SIZE = 2

# Socket stuff
IP = "127.0.0.1"
CONTROL_PORT = 60000 #int(sys.argv[1])
MY_PORT = 0

class ErrorResponseReceived(Exception):
    pass

def receive_response(f):
    line = f.readline()[:-len(NEWLINE)]
    
    idx = line.find(" ")
    code = int(line[:idx])
    message = line[idx+1:]
    
    return code, message

def send_command(s, cmd, args=""):
    print("sending "+ cmd)
    s.sendall((cmd + " " + str(args) + NEWLINE).encode(ENCODING))
    
def receive_data(s):
    conn, addr = s.accept()
    
    # Receive data
    header_bytes = conn.recv(HEADER_SIZE)
    data_size = int.from_bytes(header_bytes, byteorder='big')
    data_bytes = conn.recv(data_size)
    
    # Close socket
    conn.close()
    
    return data_bytes

def check_response(code, message):
    if code == 400:
        print(message)
        raise ErrorResponseReceived
        
def send_command_and_check(s, f, cmd, args=""):
    send_command(s, cmd, args)
    code, message = receive_response(f)
    check_response(code, message)
        
def get_dirs_and_files(listen_socket):
    print("listening to data")
    file_list = receive_data(listen_socket).decode(ENCODING)
    if len(file_list) == 0:
        file_list = []
    else:
        file_list = file_list.split(NEWLINE)
    dirs = []
    files = []
    for name in file_list:
        name, filetype = name.split(":")
        if filetype == "d":
            dirs.append(name)
        else:
            files.append(name)
    return dirs, files
        
def DFS(control_socket, data_socket, f, dirs, files, target):
    # If file is found
    if target in files:
        # Send RETR command to receive the data
        send_command_and_check(control_socket, f, "DDIR", "test_folder")
        
        # Send DELE command to delete the data from the server
        return True
    
    # If no file found and no directories exist under the current directory
    elif len(dirs) == 0:
        # Send CDUP command
        send_command_and_check(control_socket, f, "CDUP")
        return None
    
    # Continue search if target does not exist but there are directories to look into
    else:
        for dirname in dirs:
            # Send CWD command to descend
            send_command_and_check(control_socket, f, "CWD", dirname)
            
            # Send NLST command for the new directory
            send_command_and_check(control_socket, f, "NLST")
            
            # Get new file/dir lists
            dirs_new, files_new = get_dirs_and_files(data_socket)
            
            # Call DFS recursively
            retval = DFS(control_socket, data_socket, f, dirs_new, files_new, target)
            
            if retval is not None:
                return retval
        
        # Not found
        send_command_and_check(control_socket, f, "CDUP")
        return None
        

# Start listening to the data connection
listen_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
listen_socket.bind((IP, 0))
MY_PORT = listen_socket.getsockname()[1]
listen_socket.listen(1)

# Connect to the server from the control connection
s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
s.connect((IP, CONTROL_PORT))

# Readfile
f = s.makefile(buffering=1, encoding=ENCODING, newline=NEWLINE)

try:
    # Auth & port
    send_command_and_check(s, f, "PORT", MY_PORT)
    
    # Find, get and delete the file
    send_command_and_check(s, f, "NLST")
    dirs, files = get_dirs_and_files(listen_socket)
    file = DFS(s, listen_socket, f, dirs, files, TARGET_FILENAME)
    
    
    # Quit
    send_command_and_check(s, f, "QUIT")

except ErrorResponseReceived:
    pass
    
finally:
    s.close()
    listen_socket.close()

