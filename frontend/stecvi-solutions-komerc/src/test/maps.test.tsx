import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { expect,it,vi } from "vitest";
import GoogleMapPicker from "../features/customer/components/google_maps_picker";
it("a failed Maps script can be retried without leaving duplicate scripts",async()=>{
 vi.stubEnv("VITE_GOOGLE_MAPS_API_KEY","test-key");
 try {
  render(<GoogleMapPicker value="" onChange={()=>{}}/>);let script=document.querySelector<HTMLScriptElement>('script[src*="maps.googleapis.com"]');expect(script).not.toBeNull();fireEvent.error(script!);await screen.findByRole("alert");expect(window.googleMapsLoader).toBeUndefined();
  fireEvent.click(screen.getByRole("button",{name:/ponovo/}));await waitFor(()=>expect(document.querySelectorAll('script[src*="maps.googleapis.com"]').length).toBe(1));script=document.querySelector('script[src*="maps.googleapis.com"]');expect(script).not.toBeNull();
 } finally {vi.unstubAllEnvs();document.querySelectorAll('script[src*="maps.googleapis.com"]').forEach(script=>script.remove());window.googleMapsLoader=undefined;}
});
