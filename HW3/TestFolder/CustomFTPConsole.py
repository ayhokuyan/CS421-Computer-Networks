# -*- coding: utf-8 -*-
import socket
import sys

ENCODING = "ascii"
NEWLINE = "\r\n"
USERNAME = "bilkent"
PASS = "cs421"

HEADER_SIZE = 2

# Socket stuff
IP = "127.0.0.1"
CONTROL_PORT =60000 # int(sys.argv[1])
MY_PORT = 0
SERVER_PORT = 0

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

def send_data(client_port, header, data,):
	try:
		# Open data connection
		data_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
		data_socket.connect((IP, SERVER_PORT))
		
		# Send data
		data_socket.sendall(header)
		data_socket.sendall(data)
	
	except Exception as e:
		raise e
	
	finally:
		data_socket.close()

def receive_data(s):
	conn, addr = s.accept()
	
	# Receive data
	header_bytes = conn.recv(HEADER_SIZE)
	data_size = int.from_bytes(header_bytes, byteorder='big')
	print("DEBUG PRINT: DATA SIZE" + str(data_size))

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

def get_server_port(listen_socket):
	data = receive_data(listen_socket).decode(ENCODING)
	print("DEBUG PRINT: SERVER PORT" + data)
	return int(data)


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
	print("DEBUG PRINT: FILES AND FOLDERS")
	print(dirs)
	print(files)
	return dirs, files
		
		

# Start listening to the data connection
listen_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
listen_socket.bind((IP, MY_PORT))
MY_PORT = listen_socket.getsockname()[1]
listen_socket.listen(1)

# Connect to the server from the control connection
s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
s.connect((IP, CONTROL_PORT))

# Readfile
f = s.makefile(buffering=1, encoding=ENCODING, newline=NEWLINE)

try:
		
	print("Client data port is " + str(MY_PORT))
	while(True):
		print("Enter the command:")
		command = input().split()
		if(len(command) == 1):
			send_command_and_check(s, f, command[0])
			if(command[0] == "GPRT"):
				SERVER_PORT = get_server_port(listen_socket)
			elif(command[0] == "NLST"):
				get_dirs_and_files(listen_socket)
			elif(command[0] == "RETR"):
				receive_data(listen_socket)			
		elif(len(command) == 2):
			send_command_and_check(s, f, command[0], command[1])
		else:
			print("Invalid command")

except ErrorResponseReceived:
	pass

finally:
	s.close()
	listen_socket.close()

