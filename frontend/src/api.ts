let csrf: {token:string;headerName:string}|null = null;
export class ApiError extends Error { constructor(public status:number,message:string) {super(message);} }
export async function api<T>(path:string, options:RequestInit={}):Promise<T> {
 const mutation = options.method && options.method !== 'GET';
 if(mutation && !csrf) {const r=await fetch('/api/csrf',{credentials:'same-origin',cache:'no-store'});if(!r.ok)throw new ApiError(r.status,'Unable to establish a secure connection.');csrf=await r.json();}
 const response=await fetch('/api'+path,{...options,credentials:'same-origin',cache:'no-store',headers:{'Content-Type':'application/json',...(mutation&&csrf?{[csrf.headerName]:csrf.token}:{}),...options.headers}});
 if(path==='/login'||path==='/logout'||response.status===403)csrf=null;
 if(!response.ok) {const body=await response.json().catch(()=>({message:'Something went wrong. Please try again.'}));if(response.status===401&&path!=='/login'&&path!=='/me')window.dispatchEvent(new Event('session-expired'));throw new ApiError(response.status,body.message);}
 return response.status===204?undefined as T:response.json();
}
