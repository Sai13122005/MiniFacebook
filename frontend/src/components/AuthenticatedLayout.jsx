import { Outlet } from "react-router-dom";
import Navbar from "./Navbar";

export default function AuthenticatedLayout() {
    return (
        <>
            <Navbar />
            <Outlet />
        </>
    );
}