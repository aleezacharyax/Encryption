import java.util.Arrays;

public class App {
    private static final int MOD = 26;

    // Encrypt text using Hill cipher and key matrix
    public static String encrypt(String text, int[][] keyMatrix) {
        int n = keyMatrix.length;
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < text.length(); i += n) {
            int[] vector = new int[n];
            for (int j = 0; j < n; j++) {
                vector[j] = text.charAt(i + j) - 'A';
            }
            int[] encrypted = multiplyMatrixVector(keyMatrix, vector);
            for (int val : encrypted) {
                result.append((char) ((val % MOD) + 'A'));
            }
        }
        return result.toString();
    }

    // Decrypt text using inverse key matrix
    public static String decrypt(String text, int[][] invKeyMatrix) {
        int n = invKeyMatrix.length;
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < text.length(); i += n) {
            int[] vector = new int[n];
            for (int j = 0; j < n; j++) {
                vector[j] = text.charAt(i + j) - 'A';
            }
            int[] decrypted = multiplyMatrixVector(invKeyMatrix, vector);
            for (int val : decrypted) {
                // mod 26 positive
                int ch = ((val % MOD) + MOD) % MOD;
                result.append((char) (ch + 'A'));
            }
        }
        return result.toString();
    }

    // Multiply matrix by vector mod 26
    private static int[] multiplyMatrixVector(int[][] matrix, int[] vector) {
        int n = matrix.length;
        int[] result = new int[n];
        for (int i = 0; i < n; i++) {
            int sum = 0;
            for (int j = 0; j < n; j++) {
                sum += matrix[i][j] * vector[j];
            }
            result[i] = sum % MOD;
        }
        return result;
    }

    // Compute inverse matrix mod 26
    public static int[][] inverseMatrix(int[][] matrix) {
        int n = matrix.length;
        int det = determinant(matrix, n);
        det = mod26(det);
        int detInv = modInverse(det, MOD);
        if (detInv == -1) {
            throw new IllegalArgumentException("Matrix determinant has no inverse modulo 26, matrix is not invertible");
        }

        int[][] adj = adjoint(matrix);
        int[][] inv = new int[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                inv[i][j] = mod26(adj[i][j] * detInv);
            }
        }
        return inv;
    }

    // Calculate determinant recursively
    private static int determinant(int[][] matrix, int n) {
        if (n == 1) return matrix[0][0];
        int det = 0;
        int sign = 1;
        for (int f = 0; f < n; f++) {
            int[][] temp = getCofactor(matrix, 0, f, n);
            det += sign * matrix[0][f] * determinant(temp, n - 1);
            sign = -sign;
        }
        return det;
    }

    // Get cofactor of matrix by removing row p and col q
    private static int[][] getCofactor(int[][] matrix, int p, int q, int n) {
        int[][] temp = new int[n - 1][n - 1];
        int i = 0, j = 0;
        for (int row = 0; row < n; row++) {
            if (row == p) continue;
            j = 0;
            for (int col = 0; col < n; col++) {
                if (col == q) continue;
                temp[i][j] = matrix[row][col];
                j++;
            }
            i++;
        }
        return temp;
    }

    // Calculate adjoint matrix
    private static int[][] adjoint(int[][] matrix) {
        int n = matrix.length;
        int[][] adj = new int[n][n];
        if (n == 1) {
            adj[0][0] = 1;
            return adj;
        }

        int sign;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                int[][] temp = getCofactor(matrix, i, j, n);
                sign = ((i + j) % 2 == 0) ? 1 : -1;
                adj[j][i] = mod26(sign * determinant(temp, n - 1));
            }
        }
        return adj;
    }

    // Modular inverse of a mod m using Extended Euclidean Algorithm
    private static int modInverse(int a, int m) {
        a = a % m;
        for (int x = 1; x < m; x++) {
            if ((a * x) % m == 1) return x;
        }
        return -1;
    }

    // Modulo 26 with positive result
    private static int mod26(int x) {
        x %= MOD;
        if (x < 0) x += MOD;
        return x;
    }

    // For testing
    public static void main(String[] args) {
        String plaintext = "HELLO";
        int[][] key = {
                {6,24,1},
                {13,16,10},
                {20,17,15}
        };

        String padded = plaintext;
        while (padded.length() % key.length != 0) padded += "X";

        System.out.println("Plaintext: " + padded);
        String encrypted = encrypt(padded, key);
        System.out.println("Encrypted: " + encrypted);
        int[][] invKey = inverseMatrix(key);
        System.out.println("Inverse matrix:");
        for (int[] row : invKey) System.out.println(Arrays.toString(row));
        String decrypted = decrypt(encrypted, invKey);
        System.out.println("Decrypted: " + decrypted);
    }
}
