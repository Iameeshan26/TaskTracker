#!/usr/bin/env python3
"""Live contract checks against the running application (stdlib only)."""
import http.cookiejar
import json
import sys
import urllib.error
import urllib.request

BASE = (sys.argv[1] if len(sys.argv) > 1 else 'http://127.0.0.1:5173').rstrip('/')
checks = 0

def check(condition, label):
    global checks
    assert condition, label
    checks += 1
    print('PASS:', label)

class Client:
    def __init__(self):
        self.jar = http.cookiejar.CookieJar()
        self.http = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))
        self.token = None
    def request(self, method, path, body=None, csrf=True):
        headers = {'Content-Type': 'application/json'}
        if method != 'GET' and csrf:
            if self.token is None:
                _, token = self.request('GET', '/api/csrf')
                self.token = token['token']
            headers['X-CSRF-TOKEN'] = self.token
        req = urllib.request.Request(BASE + path, data=None if body is None else json.dumps(body).encode(), headers=headers, method=method)
        try:
            result = self.http.open(req)
        except urllib.error.HTTPError as error:
            result = error
        raw = result.read()
        if path in ('/api/login', '/api/logout'):
            self.token = None
        return result.status, json.loads(raw) if raw else None
    def login(self, name, password):
        status, body = self.request('POST', '/api/login', {'username': name, 'password': password})
        check(status == 200 and body['username'] == name, name + ' login')

anonymous, alice, bob, admin, manager = Client(), Client(), Client(), Client(), Client()
check(anonymous.request('GET', '/api/tasks')[0] == 401, 'anonymous list denied')
for method in ('POST', 'PATCH', 'DELETE'):
    path = '/api/tasks' if method == 'POST' else '/api/tasks/1'
    check(anonymous.request(method, path, {}, csrf=False)[0] == 401, 'anonymous ' + method + ' denied')
check(anonymous.request('POST', '/api/login', {'username': 'alice', 'password': 'wrong'})[0] == 401, 'invalid credentials denied')
alice.login('alice', 'Alice-demo-2026!')
bob.login('bob', 'Bob-demo-2026!')
admin.login('admin', 'Admin-demo-2026!')
manager.login('manager', 'Manager-demo-2026!')
status, a = alice.request('POST', '/api/tasks', {'title': 'HTTP Alice fixture', 'description': 'Original'})
check(status == 201 and a['owner'] == 'alice' and a['status'] == 'todo', 'creation status and server ownership')
status, b = bob.request('POST', '/api/tasks', {'title': 'HTTP Bob fixture'})
check(status == 201, 'Bob fixture created')
try:
    for leader in (admin, manager):
        status, accounts = leader.request('GET', '/api/users')
        check(status == 200 and [u['username'] for u in accounts] == ['admin', 'alice', 'bob', 'manager'] and all(set(u) == {'username', 'role'} for u in accounts), 'safe assignable account directory')
        status, assigned = leader.request('POST', '/api/tasks', {'title': 'Assignment HTTP fixture', 'assignee': 'alice'})
        check(status == 201 and assigned['owner'] == 'alice' and assigned['status'] == 'todo', 'leader creates assigned task')
        try:
            check(alice.request('GET', '/api/tasks/' + str(assigned['id']))[0] == 200, 'assignee can access new task')
            status, moved = leader.request('PATCH', '/api/tasks/' + str(assigned['id']), {'assignee': 'bob'})
            check(status == 200 and moved['owner'] == 'bob', 'leader reassigns task')
            check(alice.request('GET', '/api/tasks/' + str(assigned['id']))[0] == 404, 'previous assignee loses access')
            check(bob.request('GET', '/api/tasks/' + str(assigned['id']))[0] == 200, 'new assignee gains access')
        finally:
            leader.request('DELETE', '/api/tasks/' + str(assigned['id']))
    check(alice.request('GET', '/api/users')[0] == 403, 'regular user cannot browse directory')
    check(alice.request('POST', '/api/tasks', {'title': 'Forbidden assignment', 'assignee': 'bob'})[0] == 403, 'regular user cannot assign')
    check(manager.request('POST', '/api/tasks', {'title': 'Invalid assignment', 'assignee': 'missing'})[0] == 400, 'unknown assignee rejected')
    check(all(t['owner'] == 'alice' for t in alice.request('GET', '/api/tasks')[1]), 'Alice task isolation')
    check(all(t['owner'] == 'bob' for t in bob.request('GET', '/api/tasks')[1]), 'Bob task isolation')
    for method in ('GET', 'PATCH', 'DELETE'):
        check(alice.request(method, '/api/tasks/' + str(b['id']), None if method != 'PATCH' else {'title': 'Intrusion'})[0] == 404, 'cross-user ' + method + ' hidden')
    check({t['owner'] for t in admin.request('GET', '/api/tasks')[1]} >= {'alice', 'bob'}, 'administrator sees both owners')
    check(alice.request('POST', '/api/tasks', {'title': 'Valid'}, csrf=False)[0] == 403, 'authenticated mutation requires CSRF')
    status, updated = alice.request('PATCH', '/api/tasks/' + str(a['id']), {'status': 'done'})
    check(status == 200 and updated['title'] == a['title'] and updated['description'] == 'Original', 'PATCH preserves omitted fields')
    check(all(t['status'] == 'done' and t['owner'] == 'alice' for t in alice.request('GET', '/api/tasks?status=done')[1]), 'filtered tasks remain authorized')
    for payload in ({}, {'title': 'ab'}, {'title': 'x' * 101}, {'title': 'Valid', 'description': 'x' * 501}, {'title': 'Valid', 'status': 'done'}, {'title': 'Valid', 'owner': 'bob'}):
        check(alice.request('POST', '/api/tasks', payload)[0] == 400, 'invalid create rejected: ' + str(list(payload)))
    check(admin.request('PATCH', '/api/tasks/' + str(a['id']), {'status': 'invalid'})[0] == 400, 'admin validation enforced')
    check(alice.request('GET', '/api/tasks?status=invalid')[0] == 400, 'invalid filter rejected')
    check(alice.request('PATCH', '/api/tasks/not-a-number', {'title': 'Valid'})[0] == 400, 'malformed ID rejected')
    for task in (a, b):
        check(admin.request('PATCH', '/api/tasks/' + str(task['id']), {'title': 'Administrator updated task'})[0] == 200, 'admin updates ' + task['owner'])
    check(alice.request('DELETE', '/api/tasks/' + str(a['id']))[0] == 204, 'owner deletion')
    check(admin.request('DELETE', '/api/tasks/' + str(b['id']))[0] == 204, 'admin cross-owner deletion')
    check(admin.request('GET', '/api/tasks/' + str(b['id']))[0] == 404, 'deleted task missing')
    old_cookie = '; '.join(c.name + '=' + c.value for c in alice.jar)
    check(alice.request('POST', '/api/logout')[0] == 204, 'logout succeeds')
    check(alice.request('GET', '/api/tasks')[0] == 401, 'logged-out access denied')
    try:
        urllib.request.urlopen(urllib.request.Request(BASE + '/api/tasks', headers={'Cookie': old_cookie}))
        raise AssertionError('old session accepted')
    except urllib.error.HTTPError as e:
        check(e.code == 401, 'old session cookie cannot be replayed')
finally:
    for task in (a, b):
        admin.request('DELETE', '/api/tasks/' + str(task['id']))
    bob.request('POST', '/api/logout')
    admin.request('POST', '/api/logout')
    manager.request('POST', '/api/logout')
print(f'\n{checks} live HTTP checks passed; 0 failed.')
