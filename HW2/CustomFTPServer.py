# -*- coding: utf-8 -*-
import socket
import random
import sys

ENCODING = "ascii"
NEWLINE = "\r\n"
USERNAME = "bilkent"
PASS = "cs421"

SERVER_SHUTDOWN_MESSAGE = "Server shutdown. Please fix your code according to the response message and retry."
VOCAB = ["cheesecake",
         "copper",
         "futon",
         "gasket",
         "hundred",
         "milkshake",
         "shaw",
         "spaghetti",
         "woodwind",
         "workout",
         "bear",
         "casualty",
         "flintlock",
         "hydrant",
         "hydrolysis",
         "mallet",
         "mime",
         "research",
         "smith",
         "stimulus",
         "bourgeoisie",
         "conservation",
         "future",
         "gliding",
         "inhibitor",
         "panpipe",
         "phase",
         "pocket",
         "synod",
         "tutu"]
EXTENSIONS = ["txt", "png", "jpg", "html", "bin"]
TARGET_FILENAME = "target.jpg"

HEADER_SIZE = 2
MAX_DATA_SIZE = 2**(HEADER_SIZE*8) - 1

# Socket stuff
IP = sys.argv[1]
CONTROL_PORT = int(sys.argv[2])

class NotADirectoryException(Exception):
    pass

class NotAFileException(Exception):
    pass

class NotFoundException(Exception):
    pass

class AlreadyInRootException(Exception):
    pass

class ServerShutdownException(Exception):
    pass

class File():
    def __init__(self, name, is_dir, parent=None):
        self.name = name
        self.is_dir = is_dir
        self.parent = parent
        self.data = b""
        self.children = []
        
        # Generate random data if file
        if not is_dir:
            data_size = random.randint(1, MAX_DATA_SIZE)
            data_list = random.choices(range(2**8), k=data_size)
            data_list = [num.to_bytes(1, 'big') for num in data_list]
            self.data = b"".join(data_list)
        
    def __str__(self):
        suffix = "d" if self.is_dir else "f"
        return self.name + ":" + suffix
    
    def __repr__(self):
        return str(self)
        
    def _get(self, name, is_dir):
        for child in self.children:
            if name == child.name:
                if child.is_dir == is_dir:
                    return child
                else:
                    if is_dir:
                        raise NotADirectoryException
                    else:
                        raise NotAFileException
        raise NotFoundException
        
    def add_child(self, child):
        self.children.append(child)
   
    ### FTP Commands     
    def cwd(self, name):
        return self._get(name, True)
        
    def retr(self, name):
        return self._get(name, False)
        
    def cdup(self):
        if self.parent is None:
            raise AlreadyInRootException
        return self.parent
    
    def dele(self, name):
        for child in self.children:
            if name == child.name:
                if not child.is_dir:
                    self.children.remove(child)
                    return
                else:
                    raise NotAFileException
        raise NotFoundException

    def nlst(self):
        return "\r\n".join(list(map(str, self.children)))

def send_response(s, success, info=""):
    response = "200 OK" if success else "400 " + info
    response = response + "\r\n"
    s.sendall(response.encode(ENCODING))

def receive_command(f):
    line = f.readline()[:-len(NEWLINE)]
    idx = line.find(" ")
    
    if idx == -1:
        idx = len(line)
    
    cmd = line[:idx]
    args = line[idx+1:]
    print("Command received:", cmd, args)
    return cmd, args

def shutdown():
    print(SERVER_SHUTDOWN_MESSAGE)
    raise ServerShutdownException
    
def send_data(client_port, header, data):
    try:
        # Open data connection
        data_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        data_socket.connect((IP, client_port))
        
        # Send data
        #print(header.__len__())
        data_socket.sendall(header)
        data_socket.sendall(data)
        print(int.from_bytes(header, "big"))
        #print(int.from_bytes(data, "big"))
    
    except Exception as e:
        raise e
    
    finally:
        data_socket.close()

def auth_and_port_check(f, conn):
    client_port = -1
    
    # Username check
    check = False
    cmd, args = receive_command(f)
    
    if cmd == "USER":
        if args == USERNAME:
            send_response(conn, success=True)
            check = True
        else:
            send_response(conn, success=False, info="Wrong username.")
    else:
        send_response(conn, success=False, info="Wrong command. Expecting USER.")
        
    if not check:
        return client_port
        
    # Password check
    check = False
    cmd, args = receive_command(f)
    if cmd == "PASS":
        if args == PASS:
            send_response(conn, success=True)
            check = True
        else:
            send_response(conn, success=False, info="Wrong password.")
    else:
        send_response(conn, success=False, info="Wrong command. Expecting PASS.")
    
    if not check:
        return client_port
        
    # Port check
    cmd, args = receive_command(f)
    if cmd == "PORT":
        try:
            client_port = int(args)
        except:
            send_response(conn, success=False, info="Cannot convert port to int.")
        else:
            if client_port < 1 or client_port > 65535:
                client_port = -1
                send_response(conn, success=False, info="Port outside the range.")
            else:
                send_response(conn, success=True)
    else:
        send_response(conn, success=False, info="Wrong command. Expecting PORT.")
        
    return client_port

def rand_dir_structure_generate():
    random.shuffle(VOCAB)
    root = File("root", True)
    queue = []
    cur_parent = root
    
    while True:
        # Each directory contains 1 to 5 elements
        num_files = random.randint(1, 5)
        
        for i in range(num_files):
            is_dir = True if random.random() > 0.3 or len(queue) == 0 else False
            
            # Create new files & directories 
            if len(VOCAB) > 0:
                suffix = "" if is_dir else "." + random.choice(EXTENSIONS)
                new_file = File(VOCAB.pop(0) + suffix, is_dir, cur_parent)
                cur_parent.add_child(new_file)
                
                # Add new directories to the queue to deepen the tree
                if is_dir:
                    queue.append(new_file)
        
            # Create the target file and end the procedure
            else:
                target_file = File(TARGET_FILENAME, False, cur_parent)
                
                # Load image from the disk
                with open(TARGET_FILENAME, "rb") as f:
                    target_file.data = f.read()
                
                cur_parent.add_child(target_file)
                return root
                
        # Process next node
        cur_parent = queue.pop(0)


# =============================================================================
# MAIN
# =============================================================================
if __name__ == "__main__":
    # Create directory structure
    cur_dir = rand_dir_structure_generate()
    
    # Create socket
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    
    try:
        # Listen from the control port
        s.bind((IP, CONTROL_PORT))
        s.listen(1)
        conn, addr = s.accept()
        print("Client connected.")
        
        # Readfile
        f = conn.makefile(buffering=1, encoding=ENCODING, newline=NEWLINE)
        
        # Authenticate and get client data port
        client_port = auth_and_port_check(f, conn)
        if client_port == -1:
            shutdown()
            
        # Main loop
        while True:
            cmd, args = receive_command(f)
            
            if cmd == "NLST":
                file_list = cur_dir.nlst()
                header_bytes = len(file_list).to_bytes(HEADER_SIZE, byteorder='big')
                data_bytes = file_list.encode(ENCODING)
                print(IP)
                print(client_port)
                # Send the data
                send_data(client_port, header_bytes, data_bytes)
                
                # Send OK response
                send_response(conn, success=True)
            
            elif cmd == "RETR":
                try:
                    file = cur_dir.retr(args)
                    
                except NotAFileException:
                    send_response(conn, success=False, info=args + " is a directory, not a file.")
                    shutdown()
                
                except NotFoundException:
                    send_response(conn, success=False, info=args + " is not in the current directory.")
                    shutdown()
                
                else:
                    header_bytes = len(file.data).to_bytes(HEADER_SIZE, byteorder='big')
                    data_bytes = file.data
                    
                    # Send the data
                    send_data(client_port, header_bytes, data_bytes)
                    
                    # Send OK response
                    send_response(conn, success=True)
            
            elif cmd == "DELE":
                try:
                    cur_dir.dele(args)
                    
                except NotAFileException:
                    send_response(conn, success=False, info=args + " is a directory, not a file.")
                    shutdown()
                    
                except NotFoundException:
                    send_response(conn, success=False, info=args + " is not in the current directory.")
                    shutdown()
                    
                else:
                    send_response(conn, success=True)
                    
            elif cmd == "CWD":
                try:
                    cur_dir = cur_dir.cwd(args)
                    
                except NotADirectoryException:
                    send_response(conn, success=False, info=args + " is a file, not a directory.")
                    shutdown()
                
                except NotFoundException:
                    send_response(conn, success=False, info=args + " is not in the current directory.")
                    shutdown()
                    
                else:
                    send_response(conn, success=True)
            
            elif cmd == "CDUP":
                try:
                    cur_dir = cur_dir.cdup()
                
                except AlreadyInRootException:
                    send_response(conn, success=False, info=args + " is not in the current directory.")
                    shutdown()
                    
                else:
                    send_response(conn, success=True)
            
            elif cmd == "QUIT":
                send_response(conn, success=True)
                break
            
            elif cmd in ["USER", "PASS", "PORT"]:
                send_response(conn, success=False, info=cmd + " command is already sent and processed.")
                shutdown()
                
            else:
                send_response(conn, success=False, info="Unknown command.")
                shutdown()
        
    except ServerShutdownException:
        pass
    
    except ConnectionResetError as e:
        print(e)
        
    finally:
        conn.close()
        s.close()
    
