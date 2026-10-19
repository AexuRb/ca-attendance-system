export type AttendancePeriod='weekly'|'monthly'|'semester';
export interface LedgerColumn {kind:'date'|'subtotal'|'total';label:string;date:string}
export interface LedgerRow {studentNo:string;name:string;values:(number|null)[]}
export interface LedgerPreview {period:AttendancePeriod;from:string;to:string;weekdays:number[];columns:LedgerColumn[];groups:{label:string;start:number;end:number;from:string;to:string}[];totalRows:number;sourceRows:number;revision:string;rows:LedgerRow[]}
export function localDate(date:Date){return `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`;}
export function weekRange(value:string){if(!value)return {from:'',to:''};const day=new Date(value+'T12:00:00');day.setDate(day.getDate()-((day.getDay()+6)%7));const from=localDate(day);day.setDate(day.getDate()+6);return {from,to:localDate(day)};}
export function monthRange(value:string){if(!value)return {from:'',to:''};const [y,m]=value.split('-').map(Number);return {from:`${value}-01`,to:localDate(new Date(y!,m!,0,12))};}
