export type Status = 'todo' | 'in_progress' | 'done';
export type User = {username:string;role:'USER'|'ADMIN'};
export type Task = {id:number;owner:string;title:string;description:string|null;status:Status};
export const statuses: Record<Status,string> = {todo:'To do',in_progress:'In progress',done:'Done'};
