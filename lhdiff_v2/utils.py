import hashlib

class SimilarityCalculator:
    """
    Static utility class for calculating SimHash and Levenshtein distance.
    """

    @staticmethod
    def get_simhash(text: str) -> int:
        """
        Generates a 64-bit SimHash fingerprint for the given text.
        """
        if not text: return 0
        tokens = text.split()
        if not tokens: return 0
        
        v = [0] * 64
        for token in tokens:
            # Create a stable hash for the token
            token_hash = int(hashlib.md5(token.encode('utf-8')).hexdigest(), 16)
            for i in range(64):
                bit = (token_hash >> i) & 1
                if bit == 1: v[i] += 1
                else:        v[i] -= 1
                
        fingerprint = 0
        for i in range(64):
            if v[i] > 0: fingerprint |= (1 << i)
        return fingerprint

    @staticmethod
    def get_hamming_similarity(hash1: int, hash2: int) -> float:
        """
        Calculates similarity based on Hamming distance between two SimHashes.
        Returns 1.0 (Identical) to 0.0 (Different).
        """
        x = (hash1 ^ hash2) & ((1 << 64) - 1)
        distance = bin(x).count('1')
        return 1.0 - (distance / 64.0)

    @staticmethod
    def levenshtein_similarity(s1: str, s2: str) -> float:
        """
        Standard Levenshtein Ratio (0.0 to 1.0)
        """
        if not s1 and not s2: return 1.0
        if not s1 or not s2: return 0.0
        
        if len(s1) < len(s2): 
            return SimilarityCalculator.levenshtein_similarity(s2, s1)
        
        previous_row = range(len(s2) + 1)
        for i, c1 in enumerate(s1):
            current_row = [i + 1]
            for j, c2 in enumerate(s2):
                insertions = previous_row[j + 1] + 1
                deletions = current_row[j] + 1
                substitutions = previous_row[j] + (c1 != c2)
                current_row.append(min(insertions, deletions, substitutions))
            previous_row = current_row
        
        dist = previous_row[-1]
        return 1.0 - (dist / max(len(s1), len(s2)))
