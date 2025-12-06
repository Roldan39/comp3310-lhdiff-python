def calculate_total(items):
    total = 0
    for item in items:
        total += item['price']
    return total

def apply_discount(total, discount):
    return total * (1 - discount)