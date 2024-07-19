# Insertion Sort Algorithm

The Insertion Sort algorithm is a simple sorting algorithm that builds the final sorted array one item at a time. It is an in-place comparison-based algorithm that works by dividing the input array into a sorted and an unsorted region. Initially, the sorted region contains only the first element of the array, and the unsorted region contains the remaining elements.

The algorithm iterates through the unsorted region, comparing each element with the elements in the sorted region. If an element in the unsorted region is smaller than the current element in the sorted region, it is shifted to the right to make space for the current element. This process continues until the entire array is sorted.

## Algorithm Steps

1. Start with the second element (key) in the array.
2. Compare the key with the elements in the sorted region, moving from right to left.
3. If the key is smaller than the current element, shift the current element to the right.
4. Repeat step 3 until the key is greater than or equal to the current element.
5. Insert the key into its correct position in the sorted region.
6. Repeat steps 2-5 for the remaining elements in the unsorted region.

## Pseudocode

```
function insertionSort(array)
    for i = 1 to length(array) - 1
        key = array[i]
        j = i - 1
        while j >= 0 and array[j] > key
            array[j + 1] = array[j]
            j = j - 1
        array[j + 1] = key
    return array
```

## Time Complexity

The time complexity of the Insertion Sort algorithm is O(n^2) in the worst case and average case, where n is the number of elements in the array. However, it performs well for small input sizes or partially sorted arrays.
