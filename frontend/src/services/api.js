
const API_URL = import.meta.env.VITE_API_URL;

export async function sendRequest(command, data = {}) {
    try {
       const response = await fetch(`${API_URL}/api/request`, {
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
