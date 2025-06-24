// Handles file upload and download using localStorage for demo purposes

document.getElementById('cipherForm').addEventListener('submit', async function(e) {
    e.preventDefault();
    const fileInput = document.getElementById('fileInput');
    const keyMatrix = document.getElementById('keyMatrix').value.trim();
    const action = document.querySelector('input[name="action"]:checked').value;
    const file = fileInput.files[0];
    if (!file || !keyMatrix) {
        alert('Please select a file and enter a key matrix.');
        return;
    }
    const formData = new FormData();
    formData.append('file', file);
    formData.append('keyMatrix', keyMatrix);
    const url = `http://localhost:8080/${action}`;
    const resultDiv = document.getElementById('result');
    resultDiv.textContent = 'Processing...';
    try {
        const response = await fetch(url, {
            method: 'POST',
            body: formData
        });
        if (!response.ok) {
            resultDiv.textContent = 'Error processing file.';
            return;
        }
        const blob = await response.blob();
        const downloadUrl = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = downloadUrl;
        a.download = 'output.txt';
        a.textContent = 'Download processed file';
        resultDiv.innerHTML = '';
        resultDiv.appendChild(a);
    } catch (err) {
        resultDiv.textContent = 'Error: ' + err;
    }
});

function updateFileList() {
    const filesDiv = document.getElementById('files');
    filesDiv.innerHTML = '';
    for (let i = 0; i < localStorage.length; i++) {
        const key = localStorage.key(i);
        if (key.startsWith('file_')) {
            const fileName = key.substring(5);
            const fileLink = document.createElement('a');
            fileLink.href = localStorage.getItem(key);
            fileLink.download = fileName;
            fileLink.textContent = fileName;
            fileLink.className = 'file-item';
            filesDiv.appendChild(fileLink);
            filesDiv.appendChild(document.createElement('br'));
        }
    }
}

document.addEventListener('DOMContentLoaded', updateFileList); 