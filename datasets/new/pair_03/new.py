import logging

def calculate_total(items):
    logging.info("Calculating total for %d items", len(items))
    total = 0
    for item in items:
        total += item['price']
    logging.debug("Current total: %d", total)
    return total

def apply_discount(total, discount):
    logging.info("Applying discount: %.2f%%", discount * 100)
    result = total * (1 - discount)
    return result