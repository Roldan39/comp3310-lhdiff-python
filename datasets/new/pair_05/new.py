def divide_numbers(a, b):
    try:
        return a / b
    except ZeroDivisionError:
        return None

def read_file(filename):
    try:
        with open(filename, 'r') as f:
            return f.read()
    except FileNotFoundError:
        return ""