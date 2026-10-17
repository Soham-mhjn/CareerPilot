// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import App from '../App';

afterEach(()=>{cleanup();vi.unstubAllGlobals()});
const response=(data:unknown,status=200,contentType='application/json')=>new Response(typeof data==='string'?data:JSON.stringify(data),{status,headers:{'content-type':contentType}});
describe('CareerPilot API-backed app',()=>{
 it('shows account sign-in when there is no authenticated session',async()=>{
  vi.stubGlobal('fetch',vi.fn().mockResolvedValue(response('Unauthorized',401,'text/plain')));
  render(<App/>);
  expect(await screen.findByRole('heading',{name:'Welcome back'})).toBeInTheDocument();
  expect(screen.getByLabelText('Email address')).toBeInTheDocument();
 });
 it('lets a signed-out user request a password reset link',async()=>{
  const fetchMock=vi.fn(async(input:RequestInfo|URL,init?:RequestInit)=>{
   const url=String(input);
   if(url.endsWith('/auth/me'))return response('Unauthorized',401,'text/plain');
   if(url.endsWith('/auth/csrf'))return response({token:'csrf-test'});
   if(url.endsWith('/auth/password-reset/request'))return response({message:'If an account exists for that email, a password reset link has been sent.'});
   return response('Not found',404,'text/plain');
  });
  vi.stubGlobal('fetch',fetchMock);
  render(<App/>);
  fireEvent.click(await screen.findByRole('button',{name:'Forgot password?'}));
  fireEvent.change(screen.getByRole('textbox',{name:'Email address'}),{target:{value:'person@example.test'}});
  fireEvent.click(screen.getByRole('button',{name:/Send reset link/}));
  expect(await screen.findByRole('status')).toHaveTextContent('If an account exists for that email');
  expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/auth/password-reset/request'),expect.objectContaining({method:'POST',credentials:'include'}));
 }); it('loads the authenticated user and account-scoped dashboard from the API',async()=>{
  const upcomingAt=new Date(Date.now()+10*24*60*60*1000);
  const fetchMock=vi.fn(async(input:RequestInfo|URL,init?:RequestInit)=>{
   const url=String(input);
   if(url.endsWith('/auth/me'))return response({id:4,email:'sam@example.test',fullName:'Sam Pilot'});
   if(url.endsWith('/applications/9/status-history'))return response([{fromStatus:'',toStatus:'SAVED',changedAt:'2026-10-01T10:00:00Z'},{fromStatus:'SAVED',toStatus:'APPLIED',changedAt:'2026-10-02T10:00:00Z'}]);
   if(url.endsWith('/applications'))return response([{id:9,company:'Northstar',role:'Engineer',location:'Remote',status:'APPLIED',priority:'MEDIUM',createdAt:'2026-10-01T10:00:00Z'}]);
   if(url.endsWith('/dashboard/summary'))return response({totalApplications:1,inProgress:1,interviews:0,offers:0,statusDistribution:{APPLIED:1},interviewConversion:0,offerConversion:0});
   if(url.endsWith('/resumes'))return response([]);
   if(url.endsWith('/workspace/items'))return response([{id:30,type:'INTERVIEW',title:'Planning call',company:'Northstar',notes:'Prepare questions',startsAt:upcomingAt.toISOString(),durationMinutes:45,meetingUrl:'https://example.test/call',completed:false}]);
   if(url.endsWith('/workspace/items/30')&&init?.method==='PUT')return response({id:30,type:'INTERVIEW',title:'Updated planning call',company:'Northstar',notes:'Prepare questions',startsAt:upcomingAt.toISOString(),durationMinutes:45,meetingUrl:'https://example.test/call',completed:false});
   if(url.includes('/analytics/applications'))return response({range:'all',total:0,statusDistribution:{},sourceDistribution:{},timeline:[],progressed:0,resolved:0,positiveOutcomes:0,interviewProgressionRate:0,positiveOutcomeRate:0,smallSample:true});
   if(url.endsWith('/ai/status'))return response({configured:false,provider:'DEMO_DETERMINISTIC'});
   if(url.includes('/ai/career-insights'))return response({mode:'DEMO_DETERMINISTIC',headline:'Search overview',insights:['Track outcomes'],disclaimer:'Demo'});
   return response('Not found',404,'text/plain');
  });vi.stubGlobal('fetch',fetchMock);
  render(<App/>);
  await waitFor(()=>expect(screen.getByRole('button',{name:'Account menu'})).toBeInTheDocument());
  fireEvent.click(screen.getByRole('button',{name:'Account menu'}));
  expect(screen.getByText('sam@example.test')).toBeInTheDocument();
  expect(screen.getByText('Total applications')).toBeInTheDocument();
  await waitFor(()=>expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/applications'),expect.objectContaining({credentials:'include'})));
  await screen.findByText('Northstar');
  fireEvent.click(screen.getByRole('button',{name:'View stage history for Northstar'}));
  expect(await screen.findByText('Saved → Applied')).toBeInTheDocument();
  expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/applications/9/status-history'),expect.objectContaining({credentials:'include'}));
  fireEvent.click(screen.getByRole('button',{name:'Analytics'}));
  const range=await screen.findByRole('combobox',{name:'Analytics date range'});
  fireEvent.change(range,{target:{value:'30d'}});
  await waitFor(()=>expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/analytics/applications?range=30d'),expect.objectContaining({credentials:'include'})));
  expect(screen.getByText('Job-search performance')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button',{name:'AI Career Coach'}));
  fireEvent.click(await screen.findByRole('button',{name:'Generate 30-day career insights'}));
  expect(await screen.findByText('Search overview')).toBeInTheDocument();
  expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/ai/career-insights?range=30d'),expect.objectContaining({credentials:'include'}));
  fireEvent.click(screen.getByRole('button',{name:'Interviews'}));
  const monthLabel=screen.getByLabelText('Calendar month').textContent;
  fireEvent.click(screen.getByRole('button',{name:'Next month'}));
  expect(screen.getByLabelText('Calendar month').textContent).not.toBe(monthLabel);
  expect(screen.getByRole('button',{name:'Today'})).toBeInTheDocument();
  const dateLabel=upcomingAt.toLocaleDateString();
  fireEvent.click(screen.getByRole('button',{name:`${dateLabel} · 1 items`}));
  fireEvent.click(screen.getByRole('button',{name:'Edit Planning call'}));
  expect(screen.getByRole('heading',{name:'Edit calendar event'})).toBeInTheDocument();
  fireEvent.change(screen.getByRole('textbox',{name:'Title'}),{target:{value:'Updated planning call'}});
  fireEvent.click(screen.getByRole('button',{name:'Save changes'}));
  await waitFor(()=>expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/workspace/items/30'),expect.objectContaining({method:'PUT',body:expect.stringContaining('Updated planning call')})));
  fireEvent.click(screen.getByRole('button',{name:'Notifications'}));
  expect(screen.getAllByText('Planning call').length).toBeGreaterThan(0);
  fireEvent.change(screen.getByRole('textbox',{name:'Search applications'}),{target:{value:'Northstar'}});
  fireEvent.click(await screen.findByRole('option',{name:/Northstar Engineer/}));
  expect(await screen.findByRole('heading',{name:/Applications/})).toBeInTheDocument();
 });
});

