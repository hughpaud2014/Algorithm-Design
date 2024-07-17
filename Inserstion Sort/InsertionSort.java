/*Author: Paul Hughes
 * Date: 07/15/2024
 * Description: Insertion Sort
 * Problem: Sorting an array of characters using insertion sort.
 * Input: An array of characters.
 * Output: A sorted array of characters.
 */


public class InsertionSort {

    // Function to swap two elements in an array
    static void swap(char[] s, int i, int j) { 
        char temp = s[i];
        s[i] = s[j];
        s[j] = temp;
    } // End of swap function

    public static void insertionSort(char[] s, int n) {
        int i, j;
        for (i = 1; i < n; i++) { // Iterate through the array starting from the second element
            j = i;
            while (j > 0 && s[j] < s[j - 1]) { // Compare and swap elements to the left until the current element is in its correct position
                swap(s, j, j - 1);
                j--;

                // Print the current state of the array (visualize this as a row in your 13x13 grid)
                System.out.println(new String(s)); 
            } // End of inner while loop
        } // End of outer for loop
    } // End of insertionSort function

    public static void main(String[] args) {
        char[] s = "INSERTIONSORT".toCharArray(); // Initialize the array with the word "INSERTIONSORT"
        int n = s.length;
        insertionSort(s, n); // Call the insertionSort function to sort the array
        System.out.println("Sorted array: " + new String(s)); // Print the final sorted array
    } // End of main function
} // End of InsertionSort class
