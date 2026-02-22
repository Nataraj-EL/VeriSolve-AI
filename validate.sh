#!/bin/bash

BASE_URL="http://localhost:8080/api/v1/questions"

echo "1. Testing Arithmetic..."
curl -s -X POST -F "text=What is 25 * 48?" -F "subject=APTITUDE" "$BASE_URL/solve" | json_pp
echo -e "\n\n"

echo "2. Testing Logic..."
curl -s -X POST -F "text=If all Bloops are Razzies and all Razzies are Lazzies, are all Bloops Lazzies?" -F "subject=APTITUDE" "$BASE_URL/solve" | json_pp
echo -e "\n\n"

echo "3. Testing Coding..."
curl -s -X POST -F "text=Write a Java method to reverse a string." -F "subject=CODING" "$BASE_URL/solve" | json_pp
echo -e "\n\n"
