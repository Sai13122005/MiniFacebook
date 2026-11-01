
const API_URL = "http://localhost:8080/api/request";

export async function sendRequest(command, data = {}) {
    try {
        const response = await fetch(API_URL, {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({
                command,
                ...data
            })
        });

        const result = await response.json();

        if (!response.ok) {
            throw new Error(
                result.message || "Request failed"
            );
        }

        return result;

    } catch (error) {
        console.error("API Error:", error);
        throw error;
    }
}
